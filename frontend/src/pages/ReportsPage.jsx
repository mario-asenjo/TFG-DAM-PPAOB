import { useMemo, useState } from "react";
import { formatBytes, formatDate, shortHash } from "../utils/format";

export function ReportsPage({
  canRunAnalysis,
  analyses,
  analysesLoading,
  reports,
  reportsLoading,
  reportsError,
  reportStatus,
  onRefreshReports,
  onGenerateReport,
  onDownloadReport
}) {
  const [analysisId, setAnalysisId] = useState("");

  const doneAnalyses = useMemo(() => analyses.filter((item) => item.status === "DONE"), [analyses]);

  function handleGenerate() {
    onGenerateReport(analysisId);
  }

  if (!canRunAnalysis) {
    return (
      <section className="surface-panel">
        <h2>Reports</h2>
        <p className="banner error">Requires ANALYST or ADMIN role.</p>
      </section>
    );
  }

  return (
    <>
      <section className="surface-panel">
        <div className="surface-header">
          <h2 className="surface-title">Generate Report</h2>
          {analysesLoading && <span className="chip warn">Loading analyses...</span>}
        </div>

        <div className="filters-row">
          <select className="surface-select" value={analysisId} onChange={(event) => setAnalysisId(event.target.value)}>
            <option value="">Select DONE analysis</option>
            {doneAnalyses.map((item) => (
              <option key={item.analysisId} value={item.analysisId}>
                {(item.binaryOriginalName || shortHash(item.binaryId))} - {formatDate(item.finishedAt || item.createdAt)}
              </option>
            ))}
          </select>
          <button type="button" className="surface-button-primary" onClick={handleGenerate} disabled={!analysisId}>
            Generate HTML
          </button>
        </div>

        {reportStatus && <p className="banner success">{reportStatus}</p>}
      </section>

      <section className="surface-panel">
        <div className="surface-header">
          <h2 className="surface-title">Reports</h2>
          <div className="flex items-center gap-2">
            {reportsLoading && <span className="chip warn">Loading...</span>}
            <button type="button" className="surface-button-secondary" onClick={onRefreshReports}>Refresh</button>
          </div>
        </div>

        {reportsError && <p className="banner error">{reportsError}</p>}

        {reports.length === 0 ? (
          <div className="empty-state light">
            <h3>No reports yet</h3>
            <p>Generate an HTML report from a DONE analysis.</p>
          </div>
        ) : (
          <div className="table-wrap">
            <table className="surface-table">
              <thead>
                <tr>
                  <th>Binary</th>
                  <th>Artifact</th>
                  <th>Type</th>
                  <th>Created</th>
                  <th>Size</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {reports.map((item) => (
                  <tr key={item.artifactId}>
                    <td>{item.binaryOriginalName || shortHash(item.analysisId)}</td>
                    <td>
                      <div>{item.fileName || shortHash(item.artifactId)}</div>
                      <small>{shortHash(item.artifactId)}</small>
                    </td>
                    <td>{item.type}</td>
                    <td>{formatDate(item.createdAt)}</td>
                    <td>{formatBytes(item.sizeBytes)}</td>
                    <td>
                      <button type="button" className="surface-button-secondary" onClick={() => onDownloadReport(item.artifactId)}>
                        Download
                      </button>
                    </td>
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
