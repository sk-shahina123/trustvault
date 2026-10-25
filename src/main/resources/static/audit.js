"use strict";
const eventRows = document.getElementById("events");
const auditStatus = document.getElementById("status");
const olderButton = document.getElementById("older");
let nextBefore = null;
async function loadEvents(before = null) {
    olderButton.disabled = true;
    eventRows.replaceChildren();
    auditStatus.textContent = "Loading…";
    try {
        const response = await fetch("/api/admin/audit-events" + (before ? "?before=" + encodeURIComponent(before) : ""), {
            credentials: "same-origin", cache: "no-store", headers: {Accept: "application/json"}
        });
        if (response.status === 401) { location.assign("/login"); return; }
        if (!response.ok) throw new Error(response.status === 403 ? "Administrator access required." : "Audit records could not be loaded.");
        const entries = await response.json();
        for (const entry of entries) {
            const row = document.createElement("tr");
            for (const value of [entry.id, new Date(entry.createdAt).toLocaleString(), entry.actorEmail || "Unauthenticated / unavailable", entry.documentId ?? "—", entry.eventType, entry.outcome, entry.details]) {
                const cell = document.createElement("td");
                cell.textContent = String(value ?? "");
                row.appendChild(cell);
            }
            eventRows.appendChild(row);
        }
        nextBefore = entries.length ? entries[entries.length - 1].id : null;
        olderButton.disabled = entries.length < 100;
        auditStatus.textContent = entries.length + " events shown. Newest first.";
    } catch (error) { auditStatus.textContent = error.message; }
}
document.getElementById("refresh").addEventListener("click", () => loadEvents());
olderButton.addEventListener("click", () => loadEvents(nextBefore));
loadEvents();
