import { useEffect, useState } from "react";
import { LoginPanel } from "./components/auth/LoginPanel";
import { RegisterPanel } from "./components/auth/RegisterPanel";
import { Sidebar } from "./components/layout/Sidebar";
import { TopBar } from "./components/layout/TopBar";
import { BinariesPage } from "./pages/BinariesPage";
import { DashboardPage } from "./pages/DashboardPage";
import { AuditPage } from "./pages/AuditPage";
import { AdminUsersPage } from "./pages/AdminUsersPage";
import { AnalysesPage } from "./pages/AnalysesPage";
import { ReportsPage } from "./pages/ReportsPage";
import { useHashRoute } from "./router/useHashRoute";
import { useConsoleViewModel } from "./viewmodels/useConsoleViewModel";

const PAGE_TITLES = {
  login: "Login",
  register: "Register",
  dashboard: "Dashboard",
  binaries: "Binaries",
  analyses: "Analyses",
  reports: "Reports",
  audit: "Audit Trail",
  "admin-users": "Admin Users"
};

const PUBLIC_ROUTES = ["login", "register"];
const PRIVATE_ROUTES = ["dashboard", "binaries", "analyses", "reports", "audit", "admin-users"];

export default function App() {
  const vm = useConsoleViewModel();
  const { route, navigate } = useHashRoute();
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false);

  const resolvedRoute = [...PUBLIC_ROUTES, ...PRIVATE_ROUTES].includes(route) ? route : "login";
  const isPublicRoute = PUBLIC_ROUTES.includes(resolvedRoute);

  useEffect(() => {
    if (resolvedRoute === "audit") {
      vm.loadAudit();
    }
    if (resolvedRoute === "admin-users") {
      vm.loadUsers();
    }
    if (resolvedRoute === "analyses") {
      vm.loadAnalyses();
    }
    if (resolvedRoute === "reports") {
      vm.loadAnalyses();
      vm.loadReports();
    }
  }, [resolvedRoute, vm.isAdmin]);

  useEffect(() => {
    setIsMobileNavOpen(false);
  }, [resolvedRoute]);

  useEffect(() => {
    if (vm.isBootstrappingSession) {
      return;
    }

    if (!vm.token && !isPublicRoute) {
      navigate("login");
      return;
    }

    if (vm.token && isPublicRoute) {
      navigate("dashboard");
    }
  }, [vm.token, isPublicRoute, vm.isBootstrappingSession]);

  if (vm.isBootstrappingSession) {
    return (
      <main className="public-layout">
        <section className="auth-card">
          <h2 className="auth-title">Restoring session</h2>
          <p className="auth-subtitle">Checking refresh token and loading your workspace...</p>
        </section>
      </main>
    );
  }

  if (isPublicRoute) {
    return (
      <main className="public-layout">
        {resolvedRoute === "login" && (
          <LoginPanel
            email={vm.email}
            setEmail={vm.setEmail}
            password={vm.password}
            setPassword={vm.setPassword}
            onSubmit={vm.onLoginSubmit}
            isLoading={vm.isLoginLoading}
            error={vm.error}
            onNavigateRegister={() => navigate("register")}
          />
        )}

        {resolvedRoute === "register" && (
          <RegisterPanel
            email={vm.registerEmail}
            setEmail={vm.setRegisterEmail}
            password={vm.registerPassword}
            setPassword={vm.setRegisterPassword}
            confirmPassword={vm.registerConfirmPassword}
            setConfirmPassword={vm.setRegisterConfirmPassword}
            onSubmit={vm.onRegisterSubmit}
            isLoading={vm.isRegisterLoading}
            error={vm.error}
            status={vm.registerStatus}
            onNavigateLogin={() => navigate("login")}
          />
        )}
      </main>
    );
  }

  return (
    <main className="console-layout">
      <Sidebar
        isAdmin={vm.isAdmin}
        route={resolvedRoute}
        navigate={navigate}
        onLogout={vm.onLogout}
        isMobileOpen={isMobileNavOpen}
        setIsMobileOpen={setIsMobileNavOpen}
      />

      <section className="workspace">
        <TopBar
          search={vm.search}
          setSearch={vm.setSearch}
          healthStatus={vm.health?.status}
          user={vm.user}
          onLogout={vm.onLogout}
          pageTitle={PAGE_TITLES[resolvedRoute] || "Console"}
          onToggleMobileNav={() => setIsMobileNavOpen((current) => !current)}
        />

        <section className="content-grid">
          {vm.error && <p className="banner error">{vm.error}</p>}

          {resolvedRoute === "dashboard" && (
            <DashboardPage
              healthStatus={vm.health?.status}
              roles={vm.roles}
              binaries={vm.binaries}
              canRunAnalysis={vm.canRunAnalysis}
              analysisProfile={vm.analysisProfile}
              setAnalysisProfile={vm.setAnalysisProfile}
              onRunAnalysis={vm.onRunAnalysis}
            />
          )}

          {resolvedRoute === "binaries" && (
            <BinariesPage
              selectedFile={vm.selectedFile}
              setSelectedFile={vm.setSelectedFile}
              onUploadSubmit={vm.onUploadSubmit}
              isUploadLoading={vm.isUploadLoading}
              canRunAnalysis={vm.canRunAnalysis}
              analysisProfile={vm.analysisProfile}
              setAnalysisProfile={vm.setAnalysisProfile}
              onRunAnalysis={vm.onRunAnalysis}
              analysisStatus={vm.analysisStatus}
              uploadStatus={vm.uploadStatus}
              binaries={vm.filteredBinaries}
              formatFilter={vm.formatFilter}
              setFormatFilter={vm.setFormatFilter}
            />
          )}

          {resolvedRoute === "audit" && (
            <AuditPage
              isAdmin={vm.isAdmin}
              auditEvents={vm.auditEvents}
              auditError={vm.auditError}
              auditLoading={vm.auditLoading}
              auditFilters={vm.auditFilters}
              onApplyAuditFilters={vm.applyAuditFilters}
              onClearAuditFilters={vm.clearAuditFilters}
            />
          )}

          {resolvedRoute === "admin-users" && (
            <AdminUsersPage
              isAdmin={vm.isAdmin}
              users={vm.users}
              usersError={vm.usersError}
              usersLoading={vm.usersLoading}
              roleUpdateStatus={vm.roleUpdateStatus}
              onRefreshUsers={vm.loadUsers}
              onUpdateUserRoles={vm.updateRolesForUser}
              onUpdateUserEnabled={vm.updateEnabledForUser}
            />
          )}

          {resolvedRoute === "analyses" && (
            <AnalysesPage
              canRunAnalysis={vm.canRunAnalysis}
              analyses={vm.analyses}
              analysesLoading={vm.analysesLoading}
              analysesError={vm.analysesError}
              selectedAnalysisId={vm.selectedAnalysisId}
              analysisResult={vm.analysisResult}
              analysisResultLoading={vm.analysisResultLoading}
              analysisResultError={vm.analysisResultError}
              traceArtifacts={vm.traceArtifacts}
              traceArtifactsLoading={vm.traceArtifactsLoading}
              traceArtifactsError={vm.traceArtifactsError}
              onRefreshAnalyses={vm.loadAnalyses}
              onSelectAnalysis={vm.selectAnalysis}
              onLoadAnalysisResult={vm.loadAnalysisResult}
              onLoadTraceArtifacts={vm.loadTraceArtifacts}
              onRefreshAnalysisStatus={vm.refreshAnalysisStatus}
              onDownloadTraceRaw={vm.downloadTraceRawFile}
              onDownloadTracePretty={vm.downloadTracePrettyFile}
            />
          )}

          {resolvedRoute === "reports" && (
            <ReportsPage
              canRunAnalysis={vm.canRunAnalysis}
              analyses={vm.analyses}
              analysesLoading={vm.analysesLoading}
              reports={vm.reports}
              reportsLoading={vm.reportsLoading}
              reportsError={vm.reportsError}
              reportStatus={vm.reportStatus}
              onRefreshReports={vm.loadReports}
              onGenerateReport={vm.generateReport}
              onDownloadReport={vm.downloadReportFile}
            />
          )}
        </section>
      </section>
    </main>
  );
}
