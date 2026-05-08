import { useEffect, useMemo, useState } from "react";
import { createAnalysis, createHtmlReport, downloadArtifactPretty, downloadArtifactRaw, downloadReport, getAnalysis, getAnalysisResults, getCurrentUser, getHealth, listAnalyses, listArtifacts, listAuditEvents, listBinaries, listReports, listUsers, login, logoutSession, refreshSession, register, updateUserEnabled, updateUserRoles, uploadBinary } from "../api";
import { ApiError } from "../api/client";

export function useConsoleViewModel() {
  const [health, setHealth] = useState(null);
  const [email, setEmail] = useState("admin@ppaob.local");
  const [password, setPassword] = useState("password");
  const [token, setToken] = useState("");
  const [isBootstrappingSession, setIsBootstrappingSession] = useState(true);
  const [user, setUser] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [binaries, setBinaries] = useState([]);
  const [uploadStatus, setUploadStatus] = useState("");
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [formatFilter, setFormatFilter] = useState("ALL");
  const [isLoginLoading, setIsLoginLoading] = useState(false);
  const [registerEmail, setRegisterEmail] = useState("");
  const [registerPassword, setRegisterPassword] = useState("");
  const [registerConfirmPassword, setRegisterConfirmPassword] = useState("");
  const [registerStatus, setRegisterStatus] = useState("");
  const [isRegisterLoading, setIsRegisterLoading] = useState(false);
  const [isUploadLoading, setIsUploadLoading] = useState(false);
  const [auditEvents, setAuditEvents] = useState([]);
  const [auditError, setAuditError] = useState("");
  const [auditLoading, setAuditLoading] = useState(false);
  const [auditFilters, setAuditFilters] = useState({
    action: "",
    result: "",
    userId: "",
    analysisId: "",
    binaryId: "",
    from: "",
    to: "",
    limit: 50,
    offset: 0
  });
  const [users, setUsers] = useState([]);
  const [usersError, setUsersError] = useState("");
  const [usersLoading, setUsersLoading] = useState(false);
  const [roleUpdateStatus, setRoleUpdateStatus] = useState("");
  const [analysisStatus, setAnalysisStatus] = useState("");
  const [analysisProfile, setAnalysisProfile] = useState("STATIC_BASELINE");
  const [analyses, setAnalyses] = useState([]);
  const [analysesLoading, setAnalysesLoading] = useState(false);
  const [analysesError, setAnalysesError] = useState("");
  const [selectedAnalysisId, setSelectedAnalysisId] = useState("");
  const [analysisResult, setAnalysisResult] = useState(null);
  const [analysisResultLoading, setAnalysisResultLoading] = useState(false);
  const [analysisResultError, setAnalysisResultError] = useState("");
  const [reports, setReports] = useState([]);
  const [reportsLoading, setReportsLoading] = useState(false);
  const [reportsError, setReportsError] = useState("");
  const [reportStatus, setReportStatus] = useState("");
  const [traceArtifacts, setTraceArtifacts] = useState([]);
  const [traceArtifactsLoading, setTraceArtifactsLoading] = useState(false);
  const [traceArtifactsError, setTraceArtifactsError] = useState("");

  useEffect(() => {
    getHealth().then(setHealth).catch((err) => setError(err.message));
  }, []);

  useEffect(() => {
    bootstrapSession();
  }, []);

  const roles = (user?.roles || []).map((role) => String(role).toUpperCase());
  const isAdmin = roles.includes("ADMIN");
  const canRunAnalysis = roles.includes("ANALYST") || isAdmin;

  const filteredBinaries = useMemo(() => {
    return binaries.filter((item) => {
      const matchesSearch = !search
        || item.originalName.toLowerCase().includes(search.toLowerCase())
        || item.sha256.toLowerCase().includes(search.toLowerCase());
      const matchesFormat = formatFilter === "ALL" || item.format === formatFilter;
      return matchesSearch && matchesFormat;
    });
  }, [binaries, search, formatFilter]);

  async function onLoginSubmit(event) {
    event.preventDefault();
    setError("");
    setRegisterStatus("");
    setUploadStatus("");
    setIsLoginLoading(true);

    try {
      const auth = await login(email, password);
      setToken(auth.token);
      await loadPrivateData(auth.token);
    } catch (err) {
      setUser(null);
      setToken("");
      setBinaries([]);
      setError(err.message);
    } finally {
      setIsLoginLoading(false);
    }
  }

  async function onUploadSubmit(event) {
    event.preventDefault();
    if (!selectedFile) {
      setUploadStatus("Select a file before upload");
      return;
    }

    setError("");
    setUploadStatus("Uploading...");
    setIsUploadLoading(true);

    try {
      const result = await runWithSessionRefresh((accessToken) => uploadBinary(accessToken, selectedFile));
      const items = await runWithSessionRefresh((accessToken) => listBinaries(accessToken));
      setBinaries(items);
      if (result.restoredObject) {
        setUploadStatus("Binary restored in storage and linked.");
      } else {
        setUploadStatus(result.deduplicated ? "Binary already existed (deduplicated)." : "Binary uploaded.");
      }
      setSelectedFile(null);
    } catch (err) {
      setUploadStatus("");
      setError(err.message);
    } finally {
      setIsUploadLoading(false);
    }
  }

  async function onRunAnalysis(binary, profileOverride) {
    if (!canRunAnalysis) {
      setError("Requires ANALYST or ADMIN role");
      return;
    }

    if (!binary?.objectAvailable) {
      setError("Binary missing from storage. Re-upload to enable analysis.");
      return;
    }

    setError("");
    const profile = profileOverride || analysisProfile;
    setAnalysisStatus("Scheduling analysis...");
    try {
      const created = await runWithSessionRefresh((accessToken) => createAnalysis(accessToken, binary.binaryId, profile));
      setAnalysisStatus(`${created.profile} analysis ${created.analysisId} created with status ${created.status}`);
      await loadAnalyses();
      setSelectedAnalysisId(created.analysisId);
    } catch (err) {
      setAnalysisStatus("");
      setError(err.message);
    }
  }

  async function onRegisterSubmit(event) {
    event.preventDefault();
    setError("");
    setRegisterStatus("");

    if (registerPassword !== registerConfirmPassword) {
      setError("Passwords do not match");
      return;
    }

    setIsRegisterLoading(true);
    try {
      await register(registerEmail, registerPassword);
      setRegisterStatus("Account created. You can now login.");
      setEmail(registerEmail);
      setPassword("");
      setRegisterPassword("");
      setRegisterConfirmPassword("");
    } catch (err) {
      setError(err.message);
    } finally {
      setIsRegisterLoading(false);
    }
  }

  function onLogout() {
    logoutSession().catch(() => null);
    clearAuthState();
  }

  async function bootstrapSession() {
    setIsBootstrappingSession(true);
    try {
      const refreshed = await refreshSession();
      setToken(refreshed.token);
      await loadPrivateData(refreshed.token);
    } catch {
      clearAuthState();
    } finally {
      setIsBootstrappingSession(false);
    }
  }

  async function runWithSessionRefresh(action) {
    try {
      return await action(token);
    } catch (err) {
      if (!(err instanceof ApiError) || (err.status !== 401 && err.status !== 403)) {
        throw err;
      }

      try {
        const refreshed = await refreshSession();
        setToken(refreshed.token);
        return await action(refreshed.token);
      } catch {
        clearAuthState();
        throw new Error("Session expired. Please login again.");
      }
    }
  }

  async function loadPrivateData(accessToken) {
    const profile = await getCurrentUser(accessToken);
    const normalizedProfile = {
      ...profile,
      roles: (profile.roles || []).map((role) => String(role).toUpperCase())
    };
    setUser(normalizedProfile);
    const items = await listBinaries(accessToken);
    setBinaries(items);
  }

  function clearAuthState() {
    setToken("");
    setUser(null);
    setBinaries([]);
    setUploadStatus("");
    setError("");
    setRegisterStatus("");
    setAuditEvents([]);
    setAuditError("");
    setAuditFilters({
      action: "",
      result: "",
      userId: "",
      analysisId: "",
      binaryId: "",
      from: "",
      to: "",
      limit: 50,
      offset: 0
    });
    setUsers([]);
    setUsersError("");
    setRoleUpdateStatus("");
    setAnalysisStatus("");
    setAnalysisProfile("STATIC_BASELINE");
    setAnalyses([]);
    setAnalysesError("");
    setSelectedAnalysisId("");
    setAnalysisResult(null);
    setAnalysisResultError("");
    setReports([]);
    setReportsError("");
    setReportStatus("");
    setTraceArtifacts([]);
    setTraceArtifactsError("");
  }

  async function loadAnalyses() {
    if (!token || !canRunAnalysis) {
      setAnalyses([]);
      setSelectedAnalysisId("");
      setAnalysisResult(null);
      return;
    }

    setAnalysesLoading(true);
    setAnalysesError("");
    try {
      const items = await runWithSessionRefresh((accessToken) => listAnalyses(accessToken, 100));
      setAnalyses(items);
      if (items.length === 0) {
        setSelectedAnalysisId("");
        setAnalysisResult(null);
      } else if (!selectedAnalysisId || !items.some((item) => item.analysisId === selectedAnalysisId)) {
        setSelectedAnalysisId(items[0].analysisId);
      }
    } catch (err) {
      setAnalysesError(err.message);
    } finally {
      setAnalysesLoading(false);
    }
  }

  async function loadAnalysisResult(analysisId) {
    if (!analysisId) {
      setAnalysisResult(null);
      setAnalysisResultError("");
      return;
    }

    setAnalysisResultLoading(true);
    setAnalysisResultError("");
    try {
      const payload = await runWithSessionRefresh((accessToken) => getAnalysisResults(accessToken, analysisId));
      setAnalysisResult(payload);
    } catch (err) {
      setAnalysisResult(null);
      setAnalysisResultError(err.message);
    } finally {
      setAnalysisResultLoading(false);
    }
  }

  async function refreshAnalysisStatus(analysisId) {
    if (!analysisId) {
      return;
    }

    try {
      const latest = await runWithSessionRefresh((accessToken) => getAnalysis(accessToken, analysisId));
      setAnalyses((current) => {
        if (!current.some((item) => item.analysisId === analysisId)) {
          return current;
        }
        return current.map((item) => (item.analysisId === analysisId ? latest : item));
      });
    } catch {
      return;
    }
  }

  async function loadReports() {
    if (!token || !canRunAnalysis) {
      setReports([]);
      return;
    }

    setReportsLoading(true);
    setReportsError("");
    try {
      const items = await runWithSessionRefresh((accessToken) => listReports(accessToken, 100));
      setReports(items);
    } catch (err) {
      setReportsError(err.message);
    } finally {
      setReportsLoading(false);
    }
  }

  async function generateReport(analysisId) {
    if (!analysisId) {
      setReportStatus("Select an analysis first");
      return;
    }

    setReportStatus("Generating report...");
    try {
      const report = await runWithSessionRefresh((accessToken) => createHtmlReport(accessToken, analysisId));
      setReportStatus(`Report ${report.artifactId} generated`);
      await loadReports();
    } catch (err) {
      setReportStatus(err.message);
    }
  }

  async function downloadReportFile(artifactId) {
    try {
      const { blob, fileName } = await runWithSessionRefresh((accessToken) => downloadReport(accessToken, artifactId));
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = fileName;
      anchor.click();
      URL.revokeObjectURL(url);
      setReportStatus(`Downloaded ${fileName}`);
    } catch (err) {
      setReportStatus(err.message);
    }
  }

  function selectAnalysis(analysisId) {
    setSelectedAnalysisId(analysisId || "");
  }

  async function loadAudit(nextFilters) {
    if (!token || !isAdmin) {
      setAuditEvents([]);
      return;
    }

    const effectiveFilters = nextFilters || auditFilters;

    setAuditError("");
    setAuditLoading(true);
    try {
      const events = await runWithSessionRefresh((accessToken) => listAuditEvents(accessToken, effectiveFilters));
      setAuditEvents(events);
    } catch (err) {
      setAuditError(err.message);
    } finally {
      setAuditLoading(false);
    }
  }

  async function loadTraceArtifacts(analysisId) {
    if (!analysisId || !token || !canRunAnalysis) {
      setTraceArtifacts([]);
      return;
    }

    setTraceArtifactsLoading(true);
    setTraceArtifactsError("");
    try {
      const items = await runWithSessionRefresh((accessToken) => listArtifacts(accessToken, {
        analysisId,
        type: "DYNAMIC_TRACE",
        limit: 50
      }));
      setTraceArtifacts(items);
    } catch (err) {
      setTraceArtifacts([]);
      setTraceArtifactsError(err.message);
    } finally {
      setTraceArtifactsLoading(false);
    }
  }

  async function downloadTraceRawFile(artifactId) {
    try {
      const { blob, fileName } = await runWithSessionRefresh((accessToken) => downloadArtifactRaw(accessToken, artifactId));
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = fileName;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setTraceArtifactsError(err.message);
    }
  }

  async function downloadTracePrettyFile(artifactId) {
    try {
      const { blob, fileName } = await runWithSessionRefresh((accessToken) => downloadArtifactPretty(accessToken, artifactId));
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = fileName;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setTraceArtifactsError(err.message);
    }
  }

  async function applyAuditFilters(nextFilters) {
    const merged = {
      ...auditFilters,
      ...nextFilters,
      limit: Number(nextFilters?.limit ?? auditFilters.limit ?? 50),
      offset: Number(nextFilters?.offset ?? auditFilters.offset ?? 0)
    };
    setAuditFilters(merged);
    await loadAudit(merged);
  }

  async function clearAuditFilters() {
    const cleared = {
      action: "",
      result: "",
      userId: "",
      analysisId: "",
      binaryId: "",
      from: "",
      to: "",
      limit: 50,
      offset: 0
    };
    setAuditFilters(cleared);
    await loadAudit(cleared);
  }

  async function loadUsers() {
    if (!token || !isAdmin) {
      setUsers([]);
      return;
    }

    setUsersLoading(true);
    setUsersError("");
    try {
      const accounts = await runWithSessionRefresh((accessToken) => listUsers(accessToken));
      setUsers(accounts);
    } catch (err) {
      setUsersError(err.message);
    } finally {
      setUsersLoading(false);
    }
  }

  async function updateRolesForUser(userId, rolesToSave) {
    setRoleUpdateStatus("");
    try {
      const updated = await runWithSessionRefresh((accessToken) => updateUserRoles(accessToken, userId, rolesToSave));
      setUsers((current) => current.map((item) => (item.userId === updated.userId ? updated : item)));
      setRoleUpdateStatus(`Roles updated for ${updated.email}`);
      await loadAudit();
    } catch (err) {
      setRoleUpdateStatus(err.message);
    }
  }

  async function updateEnabledForUser(userId, enabled) {
    setRoleUpdateStatus("");
    try {
      const updated = await runWithSessionRefresh((accessToken) => updateUserEnabled(accessToken, userId, enabled));
      setUsers((current) => current.map((item) => (item.userId === updated.userId ? updated : item)));
      setRoleUpdateStatus(`User ${updated.email} is now ${updated.enabled ? "enabled" : "disabled"}`);
      await loadAudit();
    } catch (err) {
      setRoleUpdateStatus(err.message);
    }
  }

  return {
    health,
    email,
    setEmail,
    password,
    setPassword,
    registerEmail,
    setRegisterEmail,
    registerPassword,
    setRegisterPassword,
    registerConfirmPassword,
    setRegisterConfirmPassword,
    registerStatus,
    token,
    isBootstrappingSession,
    user,
    roles,
    isAdmin,
    canRunAnalysis,
    selectedFile,
    setSelectedFile,
    binaries,
    filteredBinaries,
    uploadStatus,
    error,
    setError,
    search,
    setSearch,
    formatFilter,
    setFormatFilter,
    isLoginLoading,
    isRegisterLoading,
    isUploadLoading,
    auditEvents,
    auditError,
    auditLoading,
    auditFilters,
    users,
    usersError,
    usersLoading,
    roleUpdateStatus,
    analysisStatus,
    analysisProfile,
    setAnalysisProfile,
    analyses,
    analysesLoading,
    analysesError,
    selectedAnalysisId,
    analysisResult,
    analysisResultLoading,
    analysisResultError,
    reports,
    reportsLoading,
    reportsError,
    reportStatus,
    traceArtifacts,
    traceArtifactsLoading,
    traceArtifactsError,
    onLoginSubmit,
    onRegisterSubmit,
    onUploadSubmit,
    onRunAnalysis,
    onLogout,
    loadAudit,
    applyAuditFilters,
    clearAuditFilters,
    loadUsers,
    updateRolesForUser,
    updateEnabledForUser,
    loadAnalyses,
    loadAnalysisResult,
    loadTraceArtifacts,
    refreshAnalysisStatus,
    loadReports,
    generateReport,
    downloadReportFile,
    downloadTraceRawFile,
    downloadTracePrettyFile,
    selectAnalysis
  };
}
