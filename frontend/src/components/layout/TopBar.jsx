import { Menu, Search, User, Activity } from "lucide-react";

export function TopBar({ pageTitle, search, setSearch, healthStatus, user, onToggleMobileNav }) {
  const email = user?.email || "anonymous@local";
  const [namePart, domainPart = "local"] = email.split("@");

  return (
    <header className="topbar-shell">
      <div className="topbar-main">
        <div className="flex items-center gap-3 min-w-0">
          <button
            type="button"
            className="md:hidden p-2 rounded-vercel-md border border-slate-200 text-slate-600 hover:bg-slate-100"
            onClick={onToggleMobileNav}
            aria-label="Open navigation menu"
          >
            <Menu className="w-4 h-4" />
          </button>

          <h2 className="text-lg md:text-xl font-semibold tracking-tight text-slate-900 truncate">{pageTitle}</h2>

          <span className={`status-chip ${healthStatus === "UP" ? "status-up" : "status-warn"}`}>
            <Activity className="w-3 h-3" />
            API {healthStatus || "UNKNOWN"}
          </span>
        </div>

        <div className="hidden md:flex items-center gap-2 pl-4 border-l border-slate-200">
          <div className="w-8 h-8 rounded-full bg-slate-100 flex items-center justify-center border border-slate-200">
            <User className="w-4 h-4 text-slate-500" />
          </div>
          <div>
            <p className="text-sm font-medium text-slate-900 leading-none">{namePart}</p>
            <p className="text-[10px] uppercase tracking-widest text-slate-500 font-mono mt-1">{domainPart}</p>
          </div>
        </div>
      </div>

      <div className="topbar-search-wrap">
        <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          className="topbar-search"
          placeholder="Search binaries, analyses, reports"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>
    </header>
  );
}
