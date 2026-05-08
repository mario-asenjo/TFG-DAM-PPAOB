import { BinariesTable } from "../components/binaries/BinariesTable";
import { UploadPanel } from "../components/binaries/UploadPanel";

export function BinariesPage({
  selectedFile,
  setSelectedFile,
  onUploadSubmit,
  isUploadLoading,
  canRunAnalysis,
  analysisProfile,
  setAnalysisProfile,
  onRunAnalysis,
  analysisStatus,
  uploadStatus,
  binaries,
  formatFilter,
  setFormatFilter
}) {
  return (
    <div className="space-y-6">
      <UploadPanel
        selectedFile={selectedFile}
        setSelectedFile={setSelectedFile}
        onSubmit={onUploadSubmit}
        isLoading={isUploadLoading}
        canRunAnalysis={canRunAnalysis}
        uploadStatus={uploadStatus}
      />

      {analysisStatus && <p className="banner success">{analysisStatus}</p>}

      <BinariesTable
        binaries={binaries}
        formatFilter={formatFilter}
        setFormatFilter={setFormatFilter}
        canRunAnalysis={canRunAnalysis}
        analysisProfile={analysisProfile}
        setAnalysisProfile={setAnalysisProfile}
        onRunAnalysis={onRunAnalysis}
      />
    </div>
  );
}
