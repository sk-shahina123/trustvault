package com.trustvault;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Real HTTP security filters, real services, real MySQL; no mocked authorization. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTests {
    static final String PASSWORD = "TestAccountOnly!2026";
    static final String HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);
    static final String OWNER = "owner@test.invalid";
    static final String OTHER = "other@test.invalid";
    static final String VIEWER = "viewer@test.invalid";
    static final String ADMIN = "admin@test.invalid";
    static final String TEST_USER = "trustvault_test";
    static final String TEST_PASSWORD = "DisposableTestOnly!2026";
    static final String PORT = System.getenv().getOrDefault("TRUSTVAULT_TEST_PORT", "3307");
    static final String URL = "jdbc:mysql://127.0.0.1:" + PORT
            + "/trustvault_security_test?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC";
    static JdbcTemplate root;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate app;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        if (!PORT.matches("[0-9]{1,5}")) throw new IllegalArgumentException("Invalid test port");
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> TEST_USER);
        registry.add("spring.datasource.password", () -> TEST_PASSWORD);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
    }

    @BeforeAll
    static void schema() throws Exception {
        String password = System.getenv().getOrDefault("TRUSTVAULT_TEST_ROOT_PASSWORD", "TestRootOnly!2026");
        try (Connection connection = DriverManager.getConnection(URL.replace("/trustvault_security_test?", "/mysql?"), "root", password)) {
            connection.createStatement().execute("CREATE DATABASE IF NOT EXISTS trustvault_security_test");
        }
        root = new JdbcTemplate(new DriverManagerDataSource(URL, "root", password));
        for (String table : new String[]{"document_permissions", "documents", "audit_events", "app_users"}) {
            root.execute("DROP TABLE IF EXISTS " + table);
        }
        for (String file : new String[]{"001_create_app_users.sql", "002_create_documents_and_permissions.sql", "003_create_audit_events.sql"}) {
            String sql = Files.readString(Path.of("database", file)).replace("USE trustvault;", "");
            for (String statement : sql.split(";")) if (!statement.isBlank()) root.execute(statement);
        }
        root.execute("CREATE USER IF NOT EXISTS 'trustvault_test'@'%' IDENTIFIED BY 'DisposableTestOnly!2026'");
        root.execute("ALTER USER 'trustvault_test'@'%' IDENTIFIED BY 'DisposableTestOnly!2026'");
        root.execute("GRANT SELECT, INSERT ON trustvault_security_test.* TO 'trustvault_test'@'%'");
        root.execute("GRANT UPDATE, DELETE ON trustvault_security_test.documents TO 'trustvault_test'@'%'");
        root.execute("GRANT UPDATE, DELETE ON trustvault_security_test.document_permissions TO 'trustvault_test'@'%'");
    }

    @BeforeEach
    void fixtures() {
        root.execute("DROP TRIGGER IF EXISTS reject_audit_for_test");
        for (String table : new String[]{"document_permissions", "documents", "audit_events", "app_users"}) root.update("DELETE FROM " + table);
        String[][] users = {{OWNER,"OWNER"},{OTHER,"OWNER"},{VIEWER,"VIEWER"},{ADMIN,"ADMIN"}};
        for (int i=0; i<users.length; i++) root.update("INSERT INTO app_users (id,email,password_hash,role) VALUES (?,?,?,?)", i+1,users[i][0],HASH,users[i][1]);
        document(1, 1, "Owner private document", "owner-secret");
        document(2, 2, "Other private document", "other-secret");
    }

    private void document(long id, long owner, String name, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        root.update("""
                INSERT INTO documents (id,owner_id,name,original_filename,category,content_type,size_bytes,file_data)
                VALUES (?,?,?,'sample.txt','Reports','text/plain',?,?)
                """, id,owner,name,bytes.length,bytes);
    }
    private MockMultipartFile file() { return new MockMultipartFile("file","safe.txt","text/plain","hello".getBytes(StandardCharsets.UTF_8)); }
    private long count(String table) { return root.queryForObject("SELECT COUNT(*) FROM " + table,Long.class); }
    private void audit(String type, String outcome) {
        assertThat(root.queryForObject("SELECT COUNT(*) FROM audit_events WHERE event_type=? AND outcome=?",Long.class,type,outcome)).isPositive();
    }
    private void share(String email, String permission) throws Exception {
        mvc.perform(post("/api/documents/1/permissions").with(user(OWNER).roles("OWNER")).with(csrf())
                .contentType("application/x-www-form-urlencoded").param("email",email).param("permission",permission)).andExpect(status().isOk());
    }
    private MockHttpSession login(String email) throws Exception {
        return (MockHttpSession)mvc.perform(formLogin().user(email).password(PASSWORD))
                .andExpect(authenticated().withUsername(email)).andReturn().getRequest().getSession(false);
    }
    private void rejectAudit() { root.execute("CREATE TRIGGER reject_audit_for_test BEFORE INSERT ON audit_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Intentional audit failure in isolated test'"); }

    @Test void loginPageRendersWithCsrfAndRegistrationLink() throws Exception {
        mvc.perform(get("/login").accept("text/html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/register\"")));
    }

    @Test void registrationPageRendersWithoutAuthentication() throws Exception {
        mvc.perform(get("/register").accept("text/html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
    }

    @Test void registrationCannotChooseAdminAndCannotAccessExistingDocuments() throws Exception {
        mvc.perform(post("/register").with(csrf()).contentType("application/x-www-form-urlencoded")
                .param("email","NEW@test.invalid").param("password",PASSWORD).param("confirmPassword",PASSWORD)
                .param("role","ADMIN")).andExpect(status().isSeeOther()).andExpect(header().string("Location","/login?registered"));
        assertThat(app.queryForObject("SELECT role FROM app_users WHERE email='new@test.invalid'",String.class)).isEqualTo("OWNER");
        String hash=app.queryForObject("SELECT password_hash FROM app_users WHERE email='new@test.invalid'",String.class);
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD,hash)).isTrue();
        MockHttpSession session=login("new@test.invalid");
        mvc.perform(get("/api/documents/1/download").session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/audit-events").session(session)).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/documents").file(file()).param("name","My new file").param("category","Reports")
                .session(session).with(csrf())).andExpect(status().isCreated());
        audit("REGISTER","SUCCESS");
    }

    @Test void registrationRequiresCsrf() throws Exception {
        mvc.perform(post("/register").contentType("application/x-www-form-urlencoded")
                .param("email","blocked@test.invalid").param("password",PASSWORD).param("confirmPassword",PASSWORD))
                .andExpect(status().isForbidden());
        assertThat(count("app_users")).isEqualTo(4);
    }

    @Test void registrationRejectsDuplicateWeakAndMismatchedCredentials() throws Exception {
        mvc.perform(post("/register").with(csrf()).contentType("application/x-www-form-urlencoded")
                .param("email",OWNER).param("password",PASSWORD).param("confirmPassword",PASSWORD))
                .andExpect(status().isConflict());
        for (String password : new String[]{"short", "a".repeat(73), PASSWORD}) {
            mvc.perform(post("/register").with(csrf()).contentType("application/x-www-form-urlencoded")
                    .param("email","rejected@test.invalid").param("password",password).param("confirmPassword","different"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(count("app_users")).isEqualTo(4);
    }

    @Test void registrationRollsBackIfAuditWriteFails() throws Exception {
        rejectAudit();
        mvc.perform(post("/register").with(csrf()).contentType("application/x-www-form-urlencoded")
                .param("email","rollback@test.invalid").param("password",PASSWORD).param("confirmPassword",PASSWORD))
                .andExpect(status().isServiceUnavailable());
        assertThat(count("app_users")).isEqualTo(4);
    }

    @Test void realDatabaseLoginWorksAndWrongPasswordFails() throws Exception {
        login(OWNER);
        mvc.perform(formLogin().user(OWNER).password("wrong")).andExpect(unauthenticated());
    }
    @Test void ownerCanUploadAndCannotSpoofUploader() throws Exception {
        mvc.perform(multipart("/api/documents").file(file()).param("name","Upload").param("category","Reports")
                .param("owner_id","2").with(user(OWNER).roles("OWNER")).with(csrf())).andExpect(status().isCreated());
        assertThat(root.queryForObject("SELECT owner_id FROM documents WHERE name='Upload'",Long.class)).isEqualTo(1);
        audit("UPLOAD","SUCCESS");
    }
    @Test void viewerUploadRejectedAndAudited() throws Exception {
        mvc.perform(multipart("/api/documents").file(file()).param("name","Denied").param("category","Reports")
                .with(user(VIEWER).roles("VIEWER")).with(csrf())).andExpect(status().isForbidden());
        assertThat(count("documents")).isEqualTo(2); audit("UPLOAD","DENIED");
    }
    @Test void ownerListDoesNotExposeOtherOwnersMetadata() throws Exception {
        mvc.perform(get("/api/documents").with(user(OWNER).roles("OWNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(1));
    }
    @Test void viewerListStartsEmpty() throws Exception {
        mvc.perform(get("/api/documents").with(user(VIEWER).roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void otherOwnerCannotDownloadEvenWithSpoofedParameters() throws Exception {
        mvc.perform(get("/api/documents/1/download?owner_id=1&userId=1").with(user(OTHER).roles("OWNER")))
                .andExpect(status().isNotFound()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("owner-secret"))));
        audit("DOWNLOAD","DENIED");
    }
    @Test void nonexistentAndUnauthorizedDocumentsLookTheSame() throws Exception {
        String denied = mvc.perform(get("/api/documents/1/download").with(user(OTHER).roles("OWNER")))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/documents/999/download").with(user(OTHER).roles("OWNER")))
                .andExpect(status().isNotFound()).andExpect(content().string(denied));
    }
    @Test void administratorHasNoImplicitDocumentAccess() throws Exception {
        mvc.perform(get("/api/documents/1/download").with(user(ADMIN).roles("ADMIN"))).andExpect(status().isNotFound());
    }
    @Test void anonymousDownloadRejectedAndAudited() throws Exception {
        mvc.perform(get("/api/documents/1/download")).andExpect(status().isUnauthorized()); audit("DOWNLOAD","DENIED");
    }
    @Test void downloadSetsSafeHeadersAndAuditsBeforeReturningBytes() throws Exception {
        mvc.perform(get("/api/documents/1/download").with(user(OWNER).roles("OWNER")))
                .andExpect(status().isOk()).andExpect(content().string("owner-secret"))
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")));
        audit("DOWNLOAD","SUCCESS");
    }
    @Test void revocationBlocksExistingSessionAndOldUrl() throws Exception {
        MockHttpSession session = login(VIEWER);
        share(VIEWER,"READ");
        mvc.perform(get("/api/documents/1/download").session(session)).andExpect(status().isOk());
        mvc.perform(delete("/api/documents/1/permissions/3").with(user(OWNER).roles("OWNER")).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/documents/1/download").session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/documents").session(session)).andExpect(jsonPath("$.length()").value(0));
        audit("GRANT","SUCCESS"); audit("REVOKE","SUCCESS"); audit("DOWNLOAD","DENIED");
    }
    @Test void otherOwnerCannotGrantAccessToSomeoneElsesDocument() throws Exception {
        mvc.perform(post("/api/documents/1/permissions").with(user(OTHER).roles("OWNER")).with(csrf())
                .contentType("application/x-www-form-urlencoded").param("email",VIEWER).param("permission","READ"))
                .andExpect(status().isNotFound()); assertThat(count("document_permissions")).isZero(); audit("GRANT","DENIED");
    }
    @Test void otherOwnerCannotListOrRevokePermissions() throws Exception {
        share(VIEWER,"READ");
        mvc.perform(get("/api/documents/1/permissions").with(user(OTHER).roles("OWNER"))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/documents/1/permissions/3").with(user(OTHER).roles("OWNER")).with(csrf())).andExpect(status().isNotFound());
        assertThat(count("document_permissions")).isEqualTo(1);
    }
    @Test void viewerCannotReceiveEditGrant() throws Exception {
        mvc.perform(post("/api/documents/1/permissions").with(user(OWNER).roles("OWNER")).with(csrf())
                .contentType("application/x-www-form-urlencoded").param("email",VIEWER).param("permission","EDIT"))
                .andExpect(status().isForbidden()); assertThat(count("document_permissions")).isZero();
    }
    @Test void readGrantCannotEditOrDelete() throws Exception {
        share(OTHER,"READ");
        mvc.perform(multipart("/api/documents/1/edit").param("name","Changed").param("category","Reports")
                .with(user(OTHER).roles("OWNER")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/documents/1").with(user(OTHER).roles("OWNER")).with(csrf())).andExpect(status().isNotFound());
        assertThat(count("documents")).isEqualTo(2); audit("EDIT","DENIED"); audit("DELETE","DENIED");
    }
    @Test void editGrantAllowsEditingButNeverOwnershipOrPermissionChanges() throws Exception {
        share(OTHER,"EDIT");
        mvc.perform(multipart("/api/documents/1/edit").file(file()).param("name","Changed").param("category","Updated")
                .param("owner_id","2").with(user(OTHER).roles("OWNER")).with(csrf())).andExpect(status().isOk());
        assertThat(root.queryForObject("SELECT owner_id FROM documents WHERE id=1",Long.class)).isEqualTo(1);
        mvc.perform(delete("/api/documents/1").with(user(OTHER).roles("OWNER")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/documents/1/permissions").with(user(OTHER).roles("OWNER")).with(csrf())
                .contentType("application/x-www-form-urlencoded").param("email",VIEWER).param("permission","READ")).andExpect(status().isNotFound());
        audit("EDIT","SUCCESS");
    }
    @Test void revokedEditGrantStopsEditing() throws Exception {
        share(OTHER,"EDIT");
        mvc.perform(delete("/api/documents/1/permissions/2").with(user(OWNER).roles("OWNER")).with(csrf())).andExpect(status().isOk());
        mvc.perform(multipart("/api/documents/1/edit").param("name","Changed").param("category","Reports")
                .with(user(OTHER).roles("OWNER")).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void ownerDeleteRemovesPermissionsButPreservesHistory() throws Exception {
        share(VIEWER,"READ");
        mvc.perform(delete("/api/documents/1").with(user(OWNER).roles("OWNER")).with(csrf())).andExpect(status().isOk());
        assertThat(count("documents")).isEqualTo(1); assertThat(count("document_permissions")).isZero();
        audit("GRANT","SUCCESS"); audit("DELETE","SUCCESS");
        mvc.perform(get("/api/documents/1/download").with(user(VIEWER).roles("VIEWER"))).andExpect(status().isNotFound());
    }
    @Test void missingCsrfBlocksUploadAndCreatesAuditRecord() throws Exception {
        mvc.perform(multipart("/api/documents").file(file()).param("name","CSRF").param("category","Reports")
                .with(user(OWNER).roles("OWNER"))).andExpect(status().isForbidden());
        assertThat(count("documents")).isEqualTo(2); audit("UPLOAD","DENIED");
        assertThat(root.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(details,'$.reason')) FROM audit_events ORDER BY id DESC LIMIT 1",String.class)).isEqualTo("CSRF_REJECTED");
    }
    @Test void missingCsrfAlsoBlocksDeletion() throws Exception {
        mvc.perform(delete("/api/documents/1").with(user(OWNER).roles("OWNER"))).andExpect(status().isForbidden());
        assertThat(count("documents")).isEqualTo(2); audit("DELETE","DENIED");
    }
    @Test void viewerCannotReadAuditRecordsOrPage() throws Exception {
        mvc.perform(get("/api/admin/audit-events").with(user(VIEWER).roles("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(get("/audit.html").with(user(VIEWER).roles("VIEWER"))).andExpect(status().isForbidden()); audit("VIEW_AUDIT","DENIED");
    }
    @Test void administratorCanReadPaginatedAudit() throws Exception {
        share(VIEWER,"READ");
        mvc.perform(get("/api/admin/audit-events").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].eventType").value("GRANT")); audit("VIEW_AUDIT","SUCCESS");
    }
    @Test void disabledAccountCannotUseItsExistingSession() throws Exception {
        MockHttpSession session = login(OWNER);
        root.update("UPDATE app_users SET enabled=0 WHERE id=1");
        mvc.perform(get("/api/documents/1/download").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/documents").session(session)).andExpect(status().isForbidden());
    }
    @Test void demotedAdministratorCannotUseStaleSessionToReadAudit() throws Exception {
        MockHttpSession session = login(ADMIN);
        root.update("UPDATE app_users SET role='VIEWER' WHERE id=4");
        mvc.perform(get("/api/admin/audit-events").session(session)).andExpect(status().isForbidden());
    }
    @Test void invalidDocumentIdProducesControlledErrorAndAudit() throws Exception {
        mvc.perform(get("/api/documents/not-a-number/download").with(user(OWNER).roles("OWNER"))).andExpect(status().isBadRequest()); audit("DOWNLOAD","FAILURE");
    }
    @Test void unsupportedMethodsAndUnknownRoutesAreAudited() throws Exception {
        mvc.perform(put("/api/documents/1/permissions").with(user(OWNER).roles("OWNER")).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/documents/1/raw").with(user(OTHER).roles("OWNER"))).andExpect(status().isNotFound()); audit("ACCESS","DENIED");
    }
    @Test void pathTraversalIsRejectedAndAudited() throws Exception {
        mvc.perform(get("/api/documents/1/../2/download").with(user(OWNER).roles("OWNER"))).andExpect(status().isBadRequest()); audit("DOWNLOAD","DENIED");
    }
    @Test void applicationCannotModifyOrRemoveAuditRows() {
        assertThatThrownBy(() -> app.update("UPDATE audit_events SET outcome='SUCCESS' WHERE 1=0")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> app.update("DELETE FROM audit_events WHERE 1=0")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> app.execute("TRUNCATE TABLE audit_events")).isInstanceOf(DataAccessException.class);
    }
    @Test void auditFailureRollsBackUpload() throws Exception {
        rejectAudit();
        mvc.perform(multipart("/api/documents").file(file()).param("name","Must roll back").param("category","Reports")
                .with(user(OWNER).roles("OWNER")).with(csrf())).andExpect(status().isServiceUnavailable());
        assertThat(count("documents")).isEqualTo(2);
    }
    @Test void auditFailurePreventsDownload() throws Exception {
        rejectAudit();
        mvc.perform(get("/api/documents/1/download").with(user(OWNER).roles("OWNER")))
                .andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("owner-secret"))));
    }
    @Test void auditFailureRollsBackEditAndDelete() throws Exception {
        rejectAudit();
        mvc.perform(multipart("/api/documents/1/edit").param("name","Must roll back").param("category","Reports")
                .with(user(OWNER).roles("OWNER")).with(csrf())).andExpect(status().isServiceUnavailable());
        assertThat(root.queryForObject("SELECT name FROM documents WHERE id=1",String.class)).isEqualTo("Owner private document");
        mvc.perform(delete("/api/documents/1").with(user(OWNER).roles("OWNER")).with(csrf())).andExpect(status().isServiceUnavailable());
        assertThat(count("documents")).isEqualTo(2);
    }
    @Test void auditFailureRollsBackPermissionGrant() throws Exception {
        rejectAudit();
        mvc.perform(post("/api/documents/1/permissions").with(user(OWNER).roles("OWNER")).with(csrf())
                .contentType("application/x-www-form-urlencoded").param("email",VIEWER).param("permission","READ"))
                .andExpect(status().isServiceUnavailable()); assertThat(count("document_permissions")).isZero();
    }
    @Test void invalidUploadIsRejectedWithoutCreatingDocument() throws Exception {
        mvc.perform(multipart("/api/documents").file(new MockMultipartFile("file","attack.html","text/html","<script>alert(1)</script>".getBytes()))
                .param("name","Invalid").param("category","Reports").with(user(OWNER).roles("OWNER")).with(csrf())).andExpect(status().isBadRequest());
        assertThat(count("documents")).isEqualTo(2); audit("UPLOAD","FAILURE");
    }
    @Test void publicReadinessWorksWithoutExposingConfiguration() throws Exception {
        mvc.perform(get("/healthz")).andExpect(status().isOk()).andExpect(content().string("OK"));
        mvc.perform(get("/application-cloud.properties").with(user(VIEWER).roles("VIEWER"))).andExpect(status().isNotFound());
    }
}
