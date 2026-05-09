import { useEffect, useState } from "react";
import { formatDate } from "../utils/format";

export function AuditPage({
  isAdmin,
  auditEvents,
  auditError,
  auditLoading,
  auditFilters,
  onApplyAuditFilters,
  onClearAuditFilters
}) {
  const [draftFilters, setDraftFilters] = useState(auditFilters);

  useEffect(() => {
    setDraftFilters(auditFilters);
  }, [auditFilters]);

  if (!isAdmin) {
    return (
      <section className="surface-panel">
        <h2>Audit Trail</h2>
        <p className="banner error">Requires ADMIN role.</p>
      </section>
    );
  }

  return (
    <>
      <section className="surface-panel">
        <div className="surface-header">
          <h2 className="surface-title">Audit Trail</h2>
          <div className="flex items-center gap-2">
            {auditLoading && <span className="chip warn">Loading...</span>}
            <button type="button" className="surface-button-secondary" onClick={() => onApplyAuditFilters(draftFilters)}>Apply filters</button>
            <button type="button" className="surface-button-secondary" onClick={onClearAuditFilters}>Clear</button>
          </div>
        </div>

        <div className="filters-row">
          <input className="surface-input" placeholder="action" value={draftFilters.action || ""} onChange={(e) => setDraftFilters((c) => ({ ...c, action: e.target.value }))} />
          <input className="surface-input" placeholder="result" value={draftFilters.result || ""} onChange={(e) => setDraftFilters((c) => ({ ...c, result: e.target.value }))} />
          <input className="surface-input" placeholder="userId" value={draftFilters.userId || ""} onChange={(e) => setDraftFilters((c) => ({ ...c, userId: e.target.value }))} />
          <input className="surface-input" placeholder="analysisId" value={draftFilters.analysisId || ""} onChange={(e) => setDraftFilters((c) => ({ ...c, analysisId: e.target.value }))} />
          <input className="surface-input" placeholder="binaryId" value={draftFilters.binaryId || ""} onChange={(e) => setDraftFilters((c) => ({ ...c, binaryId: e.target.value }))} />
        </div>
        <div className="filters-row">
          <input className="surface-input" placeholder="from ISO (2026-05-01T00:00:00Z)" value={draftFilters.from || ""} onChange={(e) => setDraftFilters((c) => ({ ...c, from: e.target.value }))} />
          <input className="surface-input" placeholder="to ISO (2026-05-01T23:59:59Z)" value={draftFilters.to || ""} onChange={(e) => setDraftFilters((c) => ({ ...c, to: e.target.value }))} />
          <input className="surface-input" type="number" min="1" max="200" placeholder="limit" value={draftFilters.limit ?? 50} onChange={(e) => setDraftFilters((c) => ({ ...c, limit: Number(e.target.value || 50) }))} />
          <input className="surface-input" type="number" min="0" placeholder="offset" value={draftFilters.offset ?? 0} onChange={(e) => setDraftFilters((c) => ({ ...c, offset: Number(e.target.value || 0) }))} />
        </div>

        {auditError && <p className="banner error">{auditError}</p>}

        {auditEvents.length === 0 ? (
          <div className="empty-state light">
            <h3>No audit events</h3>
            <p>Events will appear when users authenticate and execute actions.</p>
          </div>
        ) : (
          <div className="table-wrap">
            <table className="surface-table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Action</th>
                  <th>Result</th>
                  <th>User</th>
                  <th>Binary</th>
                  <th>Details</th>
                </tr>
              </thead>
              <tbody>
                {auditEvents.map((event) => (
                  <tr key={event.eventId}>
                    <td>{formatDate(event.ts)}</td>
                    <td><span className="chip neutral">{event.action}</span></td>
                    <td>
                      <span className={`chip ${event.result === "SUCCESS" ? "ok" : "warn"}`}>
                        {event.result}
                      </span>
                    </td>
                    <td>
                      <div>{event.userEmail || "-"}</div>
                      <div className="text-xs text-gray-500">{event.userId}</div>
                    </td>
                    <td>
                      <div>{event.binaryOriginalName || "-"}</div>
                      <div className="text-xs text-gray-500">{event.binaryId || "-"}</div>
                    </td>
                    <td className="details-cell">{event.detailsJson}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

    </>
  );
}
