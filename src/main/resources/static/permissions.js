"use strict";

const form = document.getElementById("permissionForm");
const fields = document.getElementById("permissionFields");
const emailInput = document.getElementById("recipientEmail");
const permissionInput = document.getElementById("permission");
const message = document.getElementById("message");
const tableBody = document.getElementById("permissionsBody");
const refreshButton = document.getElementById("refreshButton");

const documentId =
    new URLSearchParams(location.search).get("documentId");

const validId = /^[1-9][0-9]{0,18}$/.test(documentId || "");

const apiUrl = "/api/documents/" +
    encodeURIComponent(documentId || "") + "/permissions";

let busy = false;
let canManage = false;

async function api(url, options = {}) {
    const headers = new Headers(options.headers || {});
    headers.set("Accept", "application/json");

    let response;
    try {
        response = await fetch(url, {
            ...options,
            headers,
            credentials: "same-origin",
            cache: "no-store"
        });
    } catch (error) {
        throw new Error(
            "Cannot reach TrustVault. Check that the application is running."
        );
    }

    if (response.status === 401 ||
            (response.redirected &&
             new URL(response.url).pathname === "/login")) {
        location.assign("/login");
        throw new Error("Please sign in again.");
    }

    const contentType = response.headers.get("content-type") || "";
    const isJson = contentType.includes("application/json") ||
        contentType.includes("application/problem+json");

    const body = isJson ? await response.json() : {};

    if (!response.ok) {
        throw new Error(
            body.message || body.detail ||
            "Request failed (HTTP " + response.status +
            "). Refresh and try again."
        );
    }

    if (!isJson) {
        throw new Error("Unexpected response from TrustVault.");
    }

    return body;
}

function syncControls() {
    fields.disabled = busy || !canManage;
    refreshButton.disabled = busy || !validId;

    tableBody.querySelectorAll("button").forEach(button => {
        button.disabled = busy || !canManage;
    });
}

function showRow(text) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");

    cell.colSpan = 3;
    cell.textContent = text;
    row.appendChild(cell);
    tableBody.replaceChildren(row);
}

function renderPermissions(entries) {
    if (!entries.length) {
        showRow("No one else has been granted access.");
        return;
    }

    const rows = entries.map(entry => {
        const row = document.createElement("tr");

        const values = [
            entry.email + (entry.enabled ? "" : " (disabled)"),
            entry.permission
        ];

        for (const value of values) {
            const cell = document.createElement("td");
            cell.textContent = value;
            row.appendChild(cell);
        }

        const actions = document.createElement("td");

        const change = document.createElement("button");
        change.type = "button";
        change.textContent = "Change";

        change.addEventListener("click", () => {
            emailInput.value = entry.email;
            permissionInput.value = entry.permission;
            permissionInput.focus();
        });

        const revoke = document.createElement("button");
        revoke.type = "button";
        revoke.className = "danger";
        revoke.textContent = "Revoke";

        revoke.addEventListener("click", () => mutate(
            apiUrl + "/" + encodeURIComponent(String(entry.userId)),
            { method: "DELETE" }
        ));

        actions.append(change, revoke);
        row.appendChild(actions);
        return row;
    });

    tableBody.replaceChildren(...rows);
}

async function loadPermissions() {
    canManage = false;
    syncControls();
    showRow("Loading permissions…");

    try {
        const entries = await api(apiUrl);

        if (!Array.isArray(entries)) {
            throw new Error("Unexpected permission list.");
        }

        renderPermissions(entries);
        canManage = true;

    } catch (error) {
        showRow(error.message);

    } finally {
        syncControls();
    }
}

async function mutate(url, options) {
    if (busy || !canManage) return;

    busy = true;
    syncControls();
    message.textContent = "Saving…";

    try {
        const csrf = await api("/api/csrf");

        const result = await api(url, {
            ...options,
            headers: { [csrf.headerName]: csrf.token }
        });

        message.textContent = result.message;
        form.reset();

        // A failed refresh must not misreport a successful save.
        await loadPermissions();

    } catch (error) {
        message.textContent = error.message +
            " Refresh permissions to check the current state before retrying.";

        canManage = false;
        showRow("Refresh permissions to check current access.");

    } finally {
        busy = false;
        syncControls();
    }
}

form.addEventListener("submit", event => {
    event.preventDefault();

    // Collect values before disabling the form.
    const body = new URLSearchParams({
        email: emailInput.value.trim(),
        permission: permissionInput.value
    });

    mutate(apiUrl, { method: "POST", body });
});

refreshButton.addEventListener("click", async () => {
    if (busy) return;

    busy = true;
    message.textContent = "";
    await loadPermissions();
    busy = false;
    syncControls();
});

async function start() {
    if (!validId) {
        document.getElementById("documentName").textContent =
            "No document selected.";

        showRow(
            "Open Manage sharing from a document on your dashboard."
        );
        return;
    }

    busy = true;
    document.getElementById("documentName").textContent =
        "Document " + documentId;

    try {
        const account = await api("/api/me");

        document.getElementById("account").textContent =
            "Signed in as " + account.email +
            " | Role: " + account.role;

        await loadPermissions();

        if (canManage) {
            const documents = await api("/api/documents");

            const selectedDocument = documents.find(
                item => String(item.id) === documentId
            );

            if (selectedDocument) {
                document.getElementById("documentName").textContent =
                    selectedDocument.name;
            }
        }

    } catch (error) {
        message.textContent = error.message;

    } finally {
        busy = false;
        syncControls();
    }
}

start();