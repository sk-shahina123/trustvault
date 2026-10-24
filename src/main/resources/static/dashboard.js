"use strict";

const accountMessage = document.getElementById("accountMessage");
const checkButton = document.getElementById("checkButton");
const statusMessage = document.getElementById("statusMessage");
const uploadForm = document.getElementById("uploadForm");
const uploadFields = document.getElementById("uploadFields");
const uploadMessage = document.getElementById("uploadMessage");
const documentsBody = document.getElementById("documentsBody");

const maxFileBytes = 5 * 1024 * 1024;

let currentAccount = null;

async function apiRequest(
    url,
    options = {},
    expectedType = "application/json"
) {
    const headers = new Headers(options.headers || {});
    headers.set("Accept", expectedType);

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

    const wentToLogin =
        response.redirected &&
        new URL(response.url).pathname === "/login";

    if (response.status === 401 || wentToLogin) {
        window.location.assign("/login");
        throw new Error("Please sign in again.");
    }

    const contentType = response.headers.get("content-type") || "";

    if (!response.ok) {
        let message = "Request failed (HTTP " + response.status + ").";

        if (response.status === 403) {
            message =
                "Access denied. If your session changed, refresh the page.";
        } else if (response.status === 413) {
            message = "Maximum file size is 5 MiB.";
        } else if (response.status >= 500) {
            message =
                "TrustVault could not complete the request. Try again later.";
        }

        if (
            contentType.includes("application/json") ||
            contentType.includes("application/problem+json")
        ) {
            const body = await response.json().catch(() => ({}));
            message = body.message || body.detail || message;
        }

        throw new Error(message);
    }

    if (!contentType.includes(expectedType)) {
        throw new Error("TrustVault returned an unexpected response.");
    }

    return expectedType === "application/json"
        ? response.json()
        : response.text();
}

function showTableMessage(message) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");

    cell.colSpan = 5;
    cell.className = "empty-state";
    cell.textContent = message;

    row.appendChild(cell);
    documentsBody.replaceChildren(row);
}

async function loadDocuments() {
    showTableMessage("Loading documents...");

    try {
        const documents = await apiRequest("/api/documents");

        if (!Array.isArray(documents)) {
            throw new Error(
                "TrustVault returned an unexpected document list."
            );
        }

        if (documents.length === 0) {
            showTableMessage("No documents available to your account.");
            return;
        }

        const rows = documents.map(entry => {
            const row = document.createElement("tr");

            const values = [
                entry.name,
                entry.ownerEmail,
                entry.category,
                new Date(entry.createdAt).toLocaleString(),
                entry.accessLevel
            ];

            for (const value of values) {
                const cell = document.createElement("td");
                cell.textContent = String(value);
                row.appendChild(cell);
            }
const downloadLink = document.createElement("a");
downloadLink.textContent = "Download";
downloadLink.href =
    "/api/documents/" +
    encodeURIComponent(String(entry.id)) +
    "/download";

downloadLink.setAttribute(
    "aria-label",
    "Download " + entry.name
);

const downloadContainer = document.createElement("div");
downloadContainer.appendChild(downloadLink);
row.firstElementChild.appendChild(downloadContainer);
            return row;
        });

        documentsBody.replaceChildren(...rows);
    } catch (error) {
        showTableMessage(error.message);
    }
}

async function loadAccount() {
    try {
        currentAccount = await apiRequest("/api/me");

        accountMessage.textContent =
            "Signed in as " + currentAccount.email +
            " | Role: " + currentAccount.role;

        const isOwner = currentAccount.role === "OWNER";

        document.getElementById("uploadPanel").hidden = !isOwner;

        document.getElementById("dashboardGrid")
            .classList.toggle("single-column", !isOwner);

        uploadFields.disabled = !isOwner;

        uploadMessage.textContent = isOwner
            ? "Choose a PDF or UTF-8 text file to upload."
            : "";

        await loadDocuments();
    } catch (error) {
        accountMessage.textContent = error.message;
        uploadFields.disabled = true;
    }
}

uploadForm.addEventListener("submit", async function (event) {
    event.preventDefault();

    const file = document.getElementById("documentFile").files[0];

    if (!file || file.size === 0 || file.size > maxFileBytes) {
        uploadMessage.textContent =
            "Choose a non-empty file no larger than 5 MiB.";
        return;
    }

    // Collect values before disabling the form fields.
    const body = new FormData(uploadForm);

    uploadFields.disabled = true;
    uploadMessage.textContent = "Uploading...";

    try {
        const csrf = await apiRequest("/api/csrf");

        await apiRequest("/api/documents", {
            method: "POST",
            headers: {
                [csrf.headerName]: csrf.token
            },
            body
        });

        // The browser sets the multipart Content-Type automatically.
        uploadForm.reset();
        uploadMessage.textContent = "Document uploaded successfully.";

        await loadDocuments();
    } catch (error) {
        uploadMessage.textContent =
            error.message +
            " Refresh the page to check your documents before retrying.";
    } finally {
        uploadFields.disabled = currentAccount?.role !== "OWNER";
    }
});

checkButton.addEventListener("click", async function () {
    statusMessage.textContent = "Checking...";
    checkButton.disabled = true;

    try {
        statusMessage.textContent = await apiRequest(
            "/api/health",
            {},
            "text/plain"
        );
    } catch (error) {
        statusMessage.textContent = error.message;
    } finally {
        checkButton.disabled = false;
    }
});

loadAccount();