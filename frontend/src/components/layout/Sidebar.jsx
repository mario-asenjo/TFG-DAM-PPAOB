import { useEffect } from "react";
import { LayoutDashboard, Binary, Activity, FileText, ShieldAlert, Users, LogOut, X } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";

function Brand() {
  return (
    <div className="px-6 py-8 border-b border-slate-200">
      <div className="flex items-center gap-2">
        <div className="w-7 h-7 bg-slate-900 rounded-vercel-sm flex items-center justify-center">
          <span className="text-white text-[11px] font-bold">P</span>
        </div>
        <h1 className="text-xl font-bold tracking-tight text-slate-900">PPAOB</h1>
      </div>
      <p className="text-xs text-slate-500 font-mono mt-2 uppercase tracking-widest">Threat Console</p>
    </div>
  );
}

function NavItems({ route, navigate, isAdmin, onClose }) {
  const items = [
    { key: "dashboard", label: "Dashboard", icon: LayoutDashboard },
    { key: "binaries", label: "Binaries", icon: Binary },
    { key: "analyses", label: "Analyses", icon: Activity },
    { key: "reports", label: "Reports", icon: FileText },
    ...(isAdmin ? [
      { key: "audit", label: "Audit Trail", icon: ShieldAlert },
      { key: "admin-users", label: "Admin Users", icon: Users }
    ] : [])
  ];

  return (
    <nav className="flex-1 px-4 py-4 space-y-1 overflow-y-auto">
      {items.map((item) => {
        const isActive = route === item.key;
        const Icon = item.icon;

        return (
          <button
            key={item.key}
            onClick={() => {
              navigate(item.key);
              onClose?.();
            }}
            className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-vercel-md transition-all duration-200 group ${
              isActive
                ? "bg-slate-900 text-white"
                : "text-slate-600 hover:text-slate-900 hover:bg-slate-100"
            }`}
            aria-current={isActive ? "page" : undefined}
          >
            <Icon className="w-4 h-4" />
            <span className="text-sm font-medium">{item.label}</span>
          </button>
        );
      })}
    </nav>
  );
}

export function Sidebar({ route, navigate, isAdmin, onLogout, isMobileOpen, setIsMobileOpen }) {
  useEffect(() => {
    if (!isMobileOpen) {
      return undefined;
    }

    const handleEsc = (event) => {
      if (event.key === "Escape") {
        setIsMobileOpen(false);
      }
    };

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    document.addEventListener("keydown", handleEsc);

    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", handleEsc);
    };
  }, [isMobileOpen, setIsMobileOpen]);

  return (
    <>
      <aside className="hidden md:flex md:w-72 h-screen bg-white border-r border-slate-200 flex-col sticky top-0">
        <Brand />
        <NavItems route={route} navigate={navigate} isAdmin={isAdmin} />
        <div className="p-4 border-t border-slate-200">
          <button
            onClick={onLogout}
            className="w-full flex items-center justify-center gap-2 px-3 py-2.5 text-sm rounded-vercel-md border border-slate-200 text-slate-600 hover:text-red-600 hover:bg-red-50 transition-colors"
          >
            <LogOut className="w-4 h-4" />
            <span>Logout</span>
          </button>
        </div>
      </aside>

      <AnimatePresence>
        {isMobileOpen && (
          <>
            <motion.button
              type="button"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setIsMobileOpen(false)}
              className="md:hidden fixed inset-0 z-40 bg-slate-950/45 backdrop-blur-[2px]"
              aria-label="Close menu overlay"
            />
            <motion.aside
              initial={{ x: "-100%" }}
              animate={{ x: 0 }}
              exit={{ x: "-100%" }}
              transition={{ type: "tween", duration: 0.22 }}
              className="md:hidden fixed left-0 top-0 z-50 h-screen w-72 bg-white border-r border-slate-200 flex flex-col"
            >
              <div className="flex items-center justify-between px-4 py-4 border-b border-slate-200">
                <div className="flex items-center gap-2">
                  <div className="w-6 h-6 bg-slate-900 rounded-vercel-sm flex items-center justify-center">
                    <span className="text-white text-[10px] font-bold">P</span>
                  </div>
                  <span className="text-sm font-semibold text-slate-900">PPAOB</span>
                </div>
                <button
                  type="button"
                  onClick={() => setIsMobileOpen(false)}
                  className="p-2 rounded-vercel-md text-slate-600 hover:bg-slate-100"
                  aria-label="Close mobile menu"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              <NavItems
                route={route}
                navigate={navigate}
                isAdmin={isAdmin}
                onClose={() => setIsMobileOpen(false)}
              />

              <div className="p-4 border-t border-slate-200">
                <button
                  onClick={() => {
                    setIsMobileOpen(false);
                    onLogout();
                  }}
                  className="w-full flex items-center justify-center gap-2 px-3 py-2.5 text-sm rounded-vercel-md border border-slate-200 text-slate-600 hover:text-red-600 hover:bg-red-50 transition-colors"
                >
                  <LogOut className="w-4 h-4" />
                  <span>Logout</span>
                </button>
              </div>
            </motion.aside>
          </>
        )}
      </AnimatePresence>
    </>
  );
}
