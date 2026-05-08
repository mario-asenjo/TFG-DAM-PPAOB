export function UploadPanel({ selectedFile, setSelectedFile, onSubmit, isLoading, canRunAnalysis, uploadStatus }) {
  return (
    <section className="surface-panel">
      <div className="surface-header">
        <h2 className="surface-title">Upload Binary</h2>
        <span className={`status-chip ${canRunAnalysis ? "status-up" : "status-warn"}`}>
          {canRunAnalysis ? "Analyst Mode" : "Viewer Mode"}
        </span>
      </div>

      <form className="upload-row" onSubmit={onSubmit} noValidate>
        <input
          className="surface-input"
          type="file"
          onChange={(event) => setSelectedFile(event.target.files?.[0] || null)}
          required
        />
        <button type="submit" className="surface-button-primary" disabled={isLoading}>
          {isLoading ? "Uploading..." : "Upload"}
        </button>
      </form>

      <p className="surface-note">Accepted in current MVP: ELF binaries up to configured backend limit.</p>
      {selectedFile && <p className="surface-chip">Selected: {selectedFile.name}</p>}
      {uploadStatus && <p className="banner success">{uploadStatus}</p>}
    </section>
  );
}
