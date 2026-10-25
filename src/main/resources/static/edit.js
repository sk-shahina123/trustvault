"use strict";
const documentId = new URLSearchParams(location.search).get("documentId");
const editFields = document.getElementById("editFields");
const editStatus = document.getElementById("status");
const editForm = document.getElementById("editForm");
const deleteButton = document.getElementById("delete");
async function request(url, options = {}) {
    const response = await fetch(url, {...options, credentials: "same-origin", cache: "no-store", headers: {Accept: "application/json", ...options.headers}});
    if (response.status === 401) { location.assign("/login"); throw new Error("Please sign in."); }
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || "Request failed.");
    return data;
}
async function initialize() {
    try {
        if (!/^[1-9][0-9]{0,18}$/.test(documentId || "")) throw new Error("Invalid document link.");
        const account = await request("/api/me");
        const documents = await request("/api/documents");
        const doc = documents.find(item => String(item.id) === documentId);
        if (account.role !== "OWNER" || !doc || !["OWNER", "EDIT"].includes(doc.accessLevel)) throw new Error("Document not found or editing unavailable.");
        document.getElementById("name").value = doc.name;
        document.getElementById("category").value = doc.category;
        deleteButton.hidden = doc.accessLevel !== "OWNER";
        editFields.disabled = false;
        editStatus.textContent = "Ready to edit.";
    } catch (error) { editStatus.textContent = error.message; }
}
async function change(method, suffix, body) {
    editFields.disabled = true;
    try {
        const csrf = await request("/api/csrf");
        const result = await request("/api/documents/" + encodeURIComponent(documentId) + suffix, {
            method, headers: {[csrf.headerName]: csrf.token}, body
        });
        editStatus.textContent = result.message;
        if (method === "DELETE") { location.assign("/"); return; }
        document.getElementById("file").value = "";
    } catch (error) { editStatus.textContent = error.message; }
    finally { editFields.disabled = false; }
}
editForm.addEventListener("submit", event => {
    event.preventDefault();
    const body = new FormData(editForm);
    change("POST", "/edit", body);
});
deleteButton.addEventListener("click", () => {
    if (confirm("Permanently delete this document and its sharing grants? Audit history will remain.")) change("DELETE", "", undefined);
});
initialize();
