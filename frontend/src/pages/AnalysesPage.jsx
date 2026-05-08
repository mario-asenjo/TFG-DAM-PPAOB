import React, { useEffect, useMemo, useState } from "react";
import { motion, AnimatePresence } from "motion/react";
import { Search, RefreshCw, ShieldCheck, Activity, Copy } from "lucide-react";
import { formatBytes, formatDate, shortHash } from "../utils/format";

const TABS = ["Summary", "Static", "Dynamic", "Signals", "Correlation", "Artifacts", "Raw JSON"];

export function AnalysesPage({
  canRunAnalysis,
  analyses,
  analysesLoading,
  analysesError,
  selectedAnalysisId,
  analysisResult,
  analysisResultLoading,
  analysisResultError,
  traceArtifacts,
  traceArtifactsLoading,
  traceArtifactsError,
  onRefreshAnalyses,
  onSelectAnalysis,
  onLoadAnalysisResult,
  onLoadTraceArtifacts,
  onRefreshAnalysisStatus,
  onDownloadTraceRaw,
  onDownloadTracePretty
}) {
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [search, setSearch] = useState("");
  const [activeTab, setActiveTab] = useState("Summary");
  const [clipboardStatus, setClipboardStatus] = useState("");

  const selected = analyses.find((item) => item.analysisId === selectedAnalysisId) || null;
  const hasRunning = analyses.some((item) => item.status === "PENDING" || item.status === "RUNNING");

  useEffect(() => {
    if (canRunAnalysis) {
      onRefreshAnalyses();
    }
  }, [canRunAnalysis]);

  useEffect(() => {
    if (!selectedAnalysisId) {
      onLoadAnalysisResult("");
      return;
    }

    if (selected?.status === "DONE") {
      onLoadAnalysisResult(selectedAnalysisId);
      return;
    }

    onLoadAnalysisResult("");
  }, [selectedAnalysisId, selected?.status]);

  useEffect(() => {
    if (!selectedAnalysisId || selected?.status !== "DONE") {
      onLoadTraceArtifacts("");
      return;
    }
    onLoadTraceArtifacts(selectedAnalysisId);
  }, [selectedAnalysisId, selected?.status]);

  useEffect(() => {
    if (!selectedAnalysisId || !selected || selected.status === "DONE" || selected.status === "FAILED") {
      return undefined;
    }

    const timer = setInterval(() => onRefreshAnalysisStatus(selectedAnalysisId), 2500);
    return () => clearInterval(timer);
  }, [selectedAnalysisId, selected?.status]);

  useEffect(() => {
    if (!hasRunning) {
      return undefined;
    }

    const timer = setInterval(() => onRefreshAnalyses(), 3500);
    return () => clearInterval(timer);
  }, [hasRunning]);

  const filtered = useMemo(() => {
    return analyses.filter((a) =>
      (statusFilter === "ALL" || a.status === statusFilter)
      && (
        a.analysisId.includes(search)
        || a.binaryId.includes(search)
        || String(a.binaryOriginalName || "").toLowerCase().includes(search.toLowerCase())
      )
    );
  }, [analyses, search, statusFilter]);

  const resultPayload = analysisResult?.results || {};
  const summary = resultPayload.summary || {};
  const metadata = resultPayload.metadata || {};
  const fileInfo = resultPayload.fileInfo || {};
  const correlation = resultPayload.correlation || {};
  const staticData = resultPayload.static || {};
  const dynamicData = resultPayload.dynamic || {};
  const signals = Array.isArray(resultPayload.signals) ? resultPayload.signals : [];
  const artifacts = Array.isArray(resultPayload.artifacts) ? resultPayload.artifacts : [];

  const elf = staticData.elf || resultPayload.elfInfo || {};
  const sections = Array.isArray(staticData.sections) ? staticData.sections : [];
  const segments = Array.isArray(staticData.segments) ? staticData.segments : [];
  const dependencies = Array.isArray(staticData.dependencies) ? staticData.dependencies : [];
  const mitigations = staticData.mitigations || {};

  const runtime = dynamicData.runtime || resultPayload.runtime || {};
  const policy = dynamicData.policy || resultPayload.policy || {};
  const topSyscalls = Array.isArray(dynamicData.topSyscalls)
    ? dynamicData.topSyscalls
    : (Array.isArray(resultPayload.topSyscalls) ? resultPayload.topSyscalls : []);
  const filesystem = dynamicData.filesystem || {};
  const filesystemPaths = Array.isArray(filesystem.paths) ? filesystem.paths : [];

  async function copyToClipboard(value, label) {
    if (!value) {
      return;
    }
    try {
      await navigator.clipboard.writeText(String(value));
      setClipboardStatus(`${label} copied`);
      setTimeout(() => setClipboardStatus(""), 1400);
    } catch {
      setClipboardStatus("Clipboard unavailable");
      setTimeout(() => setClipboardStatus(""), 1400);
    }
  }

  if (!canRunAnalysis) {
    return (
      <section className="surface-panel">
        <h2>Analyses</h2>
        <p className="banner error">Requires ANALYST or ADMIN role.</p>
      </section>
    );
  }

  return (
    <div className="space-y-8 pb-20">
      <section className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-vercel-heading">Analysis Queue</h1>
          <p className="text-geist-gray-500 mt-1">Review static and dynamic evidence without opening raw JSON.</p>
        </div>
        <button onClick={onRefreshAnalyses} className="btn-vercel-secondary flex items-center gap-2">
          <RefreshCw className={`w-4 h-4 ${analysesLoading ? "animate-spin" : ""}`} />
          <span>Refresh</span>
        </button>
      </section>

      {analysesError && <p className="banner error">{analysesError}</p>}
      {analysisResultError && <p className="banner error">{analysisResultError}</p>}
      {traceArtifactsError && <p className="banner error">{traceArtifactsError}</p>}
      {clipboardStatus && <p className="banner success">{clipboardStatus}</p>}

      <section className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        <div className="lg:col-span-5 vercel-card overflow-hidden flex flex-col h-[700px]">
          <div className="p-4 border-b vercel-border bg-geist-gray-50 space-y-3">
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-geist-gray-400" />
              <input
                className="w-full pl-10 pr-4 py-2 bg-white rounded-vercel-md text-sm vercel-border outline-none focus:ring-2 focus:ring-geist-blue-focus transition-all"
                placeholder="Search job ID or binary name..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            <div className="flex gap-2">
              {["ALL", "DONE", "RUNNING", "FAILED"].map((s) => (
                <button
                  key={s}
                  onClick={() => setStatusFilter(s)}
                  className={`px-3 py-1 rounded-full text-[11px] font-mono uppercase tracking-wider transition-all ${
                    statusFilter === s ? "bg-black text-white" : "bg-white text-geist-gray-500 vercel-border hover:bg-geist-gray-50"
                  }`}
                >
                  {s}
                </button>
              ))}
            </div>
          </div>

          <div className="flex-1 overflow-y-auto divide-y vercel-border">
            {filtered.map((a) => (
              <button
                key={a.analysisId}
                onClick={() => {
                  onSelectAnalysis(a.analysisId);
                  setActiveTab("Summary");
                }}
                className={`w-full text-left p-6 transition-all hover:bg-geist-gray-50 group relative ${
                  selectedAnalysisId === a.analysisId ? "bg-geist-gray-50" : ""
                }`}
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-[12px] font-mono text-geist-gray-400 uppercase tracking-tighter">
                    {a.analysisId}
                  </span>
                  <StatusBadge status={a.status} />
                </div>
                <h4 className="font-semibold text-sm truncate pr-10">{a.binaryOriginalName || "Unnamed binary"}</h4>
                <p className="text-xs text-geist-gray-400 mt-1 font-mono">{a.binaryId}</p>
                <p className="text-xs text-geist-gray-400 mt-2 font-mono uppercase tracking-widest">
                  {a.profile.replace("_", " ")}
                </p>
                {selectedAnalysisId === a.analysisId && (
                  <motion.div layoutId="active-job" className="absolute left-0 top-0 bottom-0 w-1 bg-black" />
                )}
              </button>
            ))}
          </div>
        </div>

        <div className="lg:col-span-7 flex flex-col gap-8">
          <AnimatePresence mode="wait">
            {selectedAnalysisId ? (
              <motion.div
                key={selectedAnalysisId}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                className="space-y-6"
              >
                {selected?.status !== "DONE" && (
                  <div className="vercel-card p-8">
                    <p className="text-sm text-geist-gray-600">
                      Analysis status is <strong>{selected?.status || "UNKNOWN"}</strong>. Result payload appears when it reaches DONE.
                    </p>
                  </div>
                )}

                {analysisResultLoading && (
                  <div className="vercel-card p-8">
                    <p className="text-sm text-geist-gray-600">Loading result payload...</p>
                  </div>
                )}

                {analysisResult ? (
                  <>
                    <div className="vercel-card p-8 bg-black text-white overflow-hidden relative">
                      <div className="absolute top-0 right-0 p-8 opacity-10">
                        <ShieldCheck className="w-32 h-32" />
                      </div>
                      <div className="relative z-10">
                        <p className="text-xs font-mono uppercase tracking-[0.2em] text-geist-gray-400 mb-4">Risk Assessment</p>
                        <h2 className="text-5xl font-bold tracking-vercel-display mb-8">{summary.riskLevel || "UNKNOWN"}</h2>
                        <div className="flex gap-12">
                          <div>
                            <p className="text-geist-gray-400 text-[10px] uppercase font-mono tracking-widest mb-1">Score</p>
                            <p className="text-2xl font-bold">{summary.riskScore || 0}/100</p>
                          </div>
                          <div>
                            <p className="text-geist-gray-400 text-[10px] uppercase font-mono tracking-widest mb-1">Findings</p>
                            <p className="text-2xl font-bold">{summary.findingsCount || 0}</p>
                          </div>
                          <div>
                            <p className="text-geist-gray-400 text-[10px] uppercase font-mono tracking-widest mb-1">Profile</p>
                            <p className="text-xl font-bold">{analysisResult.profile || metadata.requestedProfile || selected?.profile || "UNKNOWN"}</p>
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="vercel-card p-5">
                      <div className="flex flex-wrap gap-2 mb-4">
                        {TABS.map((tab) => (
                          <button
                            key={tab}
                            type="button"
                            onClick={() => setActiveTab(tab)}
                            className={`px-3 py-1 rounded-full text-[11px] font-mono uppercase tracking-wider transition-all ${
                              activeTab === tab ? "bg-black text-white" : "bg-white text-geist-gray-500 vercel-border hover:bg-geist-gray-50"
                            }`}
                          >
                            {tab}
                          </button>
                        ))}
                      </div>

                      {activeTab === "Summary" && (
                        <div className="space-y-3 text-sm">
                          <Row label="Analysis ID" value={selectedAnalysisId} onCopy={copyToClipboard} />
                          <Row label="Binary Name" value={fileInfo.originalName || selected?.binaryOriginalName || "-"} />
                          <Row label="Binary ID" value={fileInfo.binaryId || metadata.binaryId || selected?.binaryId || "-"} onCopy={copyToClipboard} />
                          <Row label="SHA-256" value={fileInfo.sha256 || "-"} onCopy={copyToClipboard} />
                          <Row label="Generated At" value={metadata.generatedAt || analysisResult.storedAt || "-"} />
                          <Row label="Risk Priority" value={correlation.priority || "-"} />
                        </div>
                      )}

                      {activeTab === "Static" && (
                        <div className="space-y-6">
                          {Object.keys(elf).length === 0 ? <Empty text="No static block available for this analysis." /> : (
                            <>
                              <SimpleTable
                                title="ELF"
                                columns={["Field", "Value"]}
                                rows={[
                                  ["isElf", String(elf.isElf ?? "-")],
                                  ["class", String(elf.class ?? "-")],
                                  ["endianness", String(elf.endianness ?? "-")],
                                  ["architecture", String(elf.architecture ?? "-")],
                                  ["entrypoint", String(elf.entrypoint ?? "-")],
                                  ["elfType", String(elf.elfType ?? "-")]
                                ]}
                              />
                              <SimpleTable
                                title="Mitigations"
                                columns={["Mitigation", "Enabled", "Status"]}
                                rows={Object.entries(mitigations).map(([name, data]) => [
                                  name,
                                  String(Boolean(data?.enabled)),
                                  String(data?.status || "UNKNOWN")
                                ])}
                                emptyText="No mitigation data"
                              />
                              <SimpleTable
                                title="Dependencies (DT_NEEDED)"
                                columns={["Library"]}
                                rows={dependencies.map((dep) => [String(dep)])}
                                emptyText="No dependencies"
                              />
                              <SimpleTable
                                title="Sections"
                                columns={["Name", "Type", "Addr", "Size", "Flags"]}
                                rows={sections.map((s) => [
                                  String(s.name || "-"),
                                  String(s.type || "-"),
                                  String(s.addr ?? "-"),
                                  String(s.size ?? "-"),
                                  String(s.flags ?? "-")
                                ])}
                                emptyText="No sections"
                              />
                              <SimpleTable
                                title="Segments"
                                columns={["Type", "VAddr", "MemSz", "FileSz", "Flags"]}
                                rows={segments.map((p) => [
                                  String(p.type || "-"),
                                  String(p.vaddr ?? "-"),
                                  String(p.memsz ?? "-"),
                                  String(p.filesz ?? "-"),
                                  String(p.flags ?? "-")
                                ])}
                                emptyText="No segments"
                              />
                            </>
                          )}
                        </div>
                      )}

                      {activeTab === "Dynamic" && (
                        <div className="space-y-6">
                          {Object.keys(runtime).length === 0 && topSyscalls.length === 0 ? <Empty text="No dynamic block available for this analysis." /> : (
                            <>
                              <SimpleTable
                                title="Runtime"
                                columns={["Field", "Value"]}
                                rows={[
                                  ["durationMs", String(runtime.durationMs ?? "-")],
                                  ["exitCode", String(runtime.exitCode ?? "-")],
                                  ["timedOut", String(runtime.timedOut ?? "-")],
                                  ["policy.name", String(policy.name ?? "-")],
                                  ["policy.deniedCount", String(policy.deniedCount ?? "-")]
                                ]}
                              />
                              <SimpleTable
                                title="Top Syscalls"
                                columns={["Name", "Number", "Count"]}
                                rows={topSyscalls.map((sc) => [
                                  String(sc.name || "-"),
                                  String(sc.number ?? "-"),
                                  String(sc.count ?? "-")
                                ])}
                                emptyText="No syscall data"
                              />
                              <SimpleTable
                                title="Filesystem Paths"
                                columns={["Syscall", "Number", "Path"]}
                                rows={filesystemPaths.map((pathRow) => [
                                  String(pathRow.syscall || "-"),
                                  String(pathRow.number ?? "-"),
                                  String(pathRow.path || "-")
                                ])}
                                emptyText="No filesystem activity"
                              />
                            </>
                          )}
                        </div>
                      )}

                      {activeTab === "Signals" && (
                        <SimpleTable
                          title="Findings"
                          columns={["ID", "Kind", "Severity", "Title"]}
                          rows={signals.map((s) => [
                            String(s.id || "-"),
                            String(s.kind || "-"),
                            String(s.severity || "-"),
                            String(s.title || "-")
                          ])}
                          emptyText="No findings"
                        />
                      )}

                      {activeTab === "Correlation" && (
                        <div className="space-y-6">
                          {Object.keys(correlation).length === 0 ? <Empty text="No correlation block available." /> : (
                            <>
                              <SimpleTable
                                title="Correlation Summary"
                                columns={["Field", "Value"]}
                                rows={[
                                  ["version", String(correlation.version || "-")],
                                  ["environmentProfile", String(correlation.environmentProfile || "-")],
                                  ["priority", String(correlation.priority || "-")],
                                  ["riskScore", String(correlation.riskScore ?? "-")]
                                ]}
                              />
                              <SimpleTable
                                title="Top Reasons"
                                columns={["Reason"]}
                                rows={(correlation.topReasons || []).map((r) => [String(r)])}
                                emptyText="No reasons"
                              />
                            </>
                          )}
                        </div>
                      )}

                      {activeTab === "Artifacts" && (
                        <div className="space-y-6">
                          {traceArtifactsLoading && <p className="text-sm text-geist-gray-600">Loading trace artifacts...</p>}

                          <SimpleTable
                            title="Result Payload Artifacts"
                            columns={["Artifact ID", "Type", "Bucket", "Object Key", "Size"]}
                            rows={artifacts.map((a) => [
                              String(a.artifactId || "-"),
                              String(a.type || "-"),
                              String(a.bucket || "-"),
                              String(a.objectKey || "-"),
                              String(a.sizeBytes ?? "-")
                            ])}
                            emptyText="No artifacts in analysis payload"
                          />

                          <div className="space-y-2">
                            <div className="flex items-center gap-2 text-preview">
                              <Activity className="w-4 h-4" />
                              <h3 className="font-bold uppercase tracking-widest text-xs">Dynamic Trace Downloads</h3>
                            </div>

                            {traceArtifacts.length === 0 ? (
                              <Empty text="No DYNAMIC_TRACE artifacts available for this analysis." />
                            ) : (
                              <div className="table-wrap">
                                <table className="surface-table">
                                  <thead>
                                    <tr>
                                      <th>Binary</th>
                                      <th>Artifact</th>
                                      <th>Created</th>
                                      <th>Size</th>
                                      <th>Actions</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {traceArtifacts.map((item) => (
                                      <tr key={item.artifactId}>
                                        <td>{item.binaryOriginalName || "-"}</td>
                                        <td>
                                          <div>{item.fileName || shortHash(item.artifactId)}</div>
                                          <small>{shortHash(item.artifactId)}</small>
                                        </td>
                                        <td>{formatDate(item.createdAt)}</td>
                                        <td>{formatBytes(item.sizeBytes)}</td>
                                        <td>
                                          <div className="flex gap-2">
                                            <button
                                              type="button"
                                              className="surface-button-secondary"
                                              onClick={() => onDownloadTraceRaw(item.artifactId)}
                                            >
                                              Raw .ndjson
                                            </button>
                                            <button
                                              type="button"
                                              className="surface-button-secondary"
                                              onClick={() => onDownloadTracePretty(item.artifactId)}
                                            >
                                              Pretty .json
                                            </button>
                                          </div>
                                        </td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              </div>
                            )}
                          </div>
                        </div>
                      )}

                      {activeTab === "Raw JSON" && (
                        <pre className="bg-geist-gray-50 rounded-vercel-md p-4 text-xs overflow-auto max-h-[460px]">
                          {JSON.stringify(resultPayload, null, 2)}
                        </pre>
                      )}
                    </div>
                  </>
                ) : (
                  <div className="vercel-card p-20 flex flex-col items-center justify-center text-center">
                    <div className="w-12 h-12 border-4 border-geist-gray-100 border-t-black rounded-full animate-spin mb-6" />
                    <h3 className="text-lg font-bold">Waiting for Results</h3>
                    <p className="text-sm text-geist-gray-500 mt-2">Select a DONE analysis or wait for processing to complete.</p>
                  </div>
                )}
              </motion.div>
            ) : (
              <div className="h-[700px] vercel-card flex flex-col items-center justify-center text-center p-12 bg-geist-gray-50 border-dashed border-2">
                <ShieldCheck className="w-12 h-12 text-geist-gray-200 mb-6" />
                <h3 className="text-xl font-bold tracking-tight">Select an analysis to review</h3>
                <p className="text-sm text-geist-gray-500 mt-2 max-w-xs leading-relaxed">
                  Choose a job from the queue to inspect static and dynamic evidence.
                </p>
              </div>
            )}
          </AnimatePresence>
        </div>
      </section>
    </div>
  );
}

function Row({ label, value, onCopy }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b vercel-border pb-2">
      <span className="text-geist-gray-400 uppercase tracking-wider text-[11px] font-mono">{label}</span>
      <div className="flex items-center gap-2 min-w-0">
        <span className="text-black truncate text-right">{value}</span>
        {onCopy && value && value !== "-" && (
          <button type="button" className="surface-button-secondary" onClick={() => onCopy(value, label)} title={`Copy ${label}`}>
            <Copy className="w-3 h-3" />
          </button>
        )}
      </div>
    </div>
  );
}

function Empty({ text }) {
  return (
    <div className="empty-state light">
      <h3>Empty</h3>
      <p>{text}</p>
    </div>
  );
}

function SimpleTable({ title, columns, rows, emptyText }) {
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 text-preview">
        <Activity className="w-4 h-4" />
        <h3 className="font-bold uppercase tracking-widest text-xs">{title}</h3>
      </div>
      {rows.length === 0 ? (
        <Empty text={emptyText || "No rows"} />
      ) : (
        <div className="table-wrap">
          <table className="surface-table">
            <thead>
              <tr>{columns.map((c) => <th key={c}>{c}</th>)}</tr>
            </thead>
            <tbody>
              {rows.map((row, idx) => (
                <tr key={`${title}-${idx}`}>{row.map((cell, cellIdx) => <td key={`${title}-${idx}-${cellIdx}`}>{cell}</td>)}</tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function StatusBadge({ status }) {
  const styles = {
    DONE: "bg-emerald-50 text-emerald-600 border-emerald-100",
    RUNNING: "bg-develop bg-opacity-5 text-develop border-develop border-opacity-10 animate-pulse",
    FAILED: "bg-ship bg-opacity-5 text-ship border-ship border-opacity-10",
    PENDING: "bg-geist-gray-50 text-geist-gray-400 border-geist-gray-100"
  };

  return (
    <span className={`px-2 py-0.5 rounded-full text-[10px] font-mono border ${styles[status] || styles.PENDING}`}>
      {status}
    </span>
  );
}
