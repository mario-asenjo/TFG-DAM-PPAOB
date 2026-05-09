import { formatBytes, formatDate, shortHash } from "../../utils/format";

export function BinariesTable({
  binaries,
  formatFilter,
  setFormatFilter,
  canRunAnalysis,
  analysisProfile,
  setAnalysisProfile,
  onRunAnalysis
}) {
  function copySha(sha) {
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(sha).catch(() => null);
    }
  }

  return (
    <section className="surface-panel">
      <div className="surface-header gap-4 flex-wrap">
        <h2 className="surface-title">Binaries</h2>
        <div className="table-filters">
          <label className="table-filter-field">
            Format
            <select className="surface-select" value={formatFilter} onChange={(event) => setFormatFilter(event.target.value)}>
              <option value="ALL">All</option>
              <option value="ELF">ELF</option>
              <option value="UNKNOWN">UNKNOWN</option>
            </select>
          </label>
          <label className="table-filter-field">
            Run profile
            <select
              className="surface-select"
              value={analysisProfile}
              onChange={(event) => setAnalysisProfile(event.target.value)}
              disabled={!canRunAnalysis}
            >
              <option value="STATIC_BASELINE">STATIC_BASELINE</option>
              <option value="DYNAMIC_BASELINE">DYNAMIC_BASELINE</option>
            </select>
          </label>
        </div>
      </div>

      {binaries.length === 0 ? (
        <div className="empty-state light">
          <h3>No binaries yet</h3>
          <p>Upload a binary to start the Upload / Run / Review workflow.</p>
        </div>
      ) : (
        <div className="table-wrap">
          <table className="surface-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>SHA-256</th>
                <th>Format</th>
                <th>Storage</th>
                <th>Size</th>
                <th>Uploaded</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {binaries.map((binary) => (
                <tr key={binary.binaryId}>
                  <td>{binary.originalName}</td>
                  <td>
                    <button
                      type="button"
                      className="link-button"
                      onClick={() => copySha(binary.sha256)}
                      title="Copy SHA-256"
                    >
                      {shortHash(binary.sha256)}
                    </button>
                  </td>
                  <td><span className="chip neutral">{binary.format}</span></td>
                  <td>
                    <span className={`chip ${binary.objectAvailable ? "ok" : "warn"}`}>
                      {binary.objectAvailable ? "AVAILABLE" : "MISSING"}
                    </span>
                  </td>
                  <td>{formatBytes(binary.sizeBytes)}</td>
                  <td>{formatDate(binary.uploadedAt)}</td>
                  <td>
                    <button
                      type="button"
                      className="surface-button-secondary"
                      disabled={!canRunAnalysis || !binary.objectAvailable}
                      title={
                        !canRunAnalysis
                          ? "Requires ANALYST or ADMIN role"
                          : !binary.objectAvailable
                            ? "Binary missing from storage. Re-upload to enable Run"
                            : `Run ${analysisProfile} analysis`
                       }
                       onClick={() => onRunAnalysis(binary, analysisProfile)}
                     >
                       Run {analysisProfile === "DYNAMIC_BASELINE" ? "Dynamic" : "Static"}
                     </button>
                   </td>
                 </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
