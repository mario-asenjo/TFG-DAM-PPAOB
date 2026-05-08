import React from "react";
import { motion } from "motion/react";
import { ShieldCheck, Database, Zap, ArrowUpRight } from "lucide-react";

const KpiCard = ({ title, value, icon: Icon, color, index }) => (
  <motion.article
    initial={{ opacity: 0, y: 10 }}
    animate={{ opacity: 1, y: 0 }}
    transition={{ delay: index * 0.1 }}
    className="vercel-card p-6 flex flex-col justify-between group"
  >
    <div className="flex items-center justify-between mb-8">
      <div className={`w-8 h-8 rounded-full flex items-center justify-center ${color} bg-opacity-10 text-opacity-100`}>
        <Icon className="w-4 h-4" />
      </div>
      <button className="text-geist-gray-400 opacity-0 group-hover:opacity-100 transition-opacity">
        <ArrowUpRight className="w-4 h-4" />
      </button>
    </div>
    <div>
      <p className="text-xs font-mono uppercase tracking-widest text-geist-gray-500 mb-2">{title}</p>
      <p className="text-4xl font-bold tracking-vercel-display -tracking-vercel-display">{value}</p>
    </div>
  </motion.article>
);

export function DashboardPage({ healthStatus, roles, binaries, canRunAnalysis, onRunAnalysis, analysisProfile, setAnalysisProfile }) {
  const recent = binaries.slice(0, 5);

  return (
    <div className="space-y-12">
      <section className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <KpiCard
          title="Total Binaries"
          value={binaries.length}
          icon={Database}
          color="text-develop"
          index={0}
        />
        <KpiCard
          title="Backend Health"
          value={healthStatus || "UNKNOWN"}
          icon={Zap}
          color="text-ship"
          index={1}
        />
        <KpiCard
          title="Active Roles"
          value={roles.length}
          icon={ShieldCheck}
          color="text-preview"
          index={2}
        />
      </section>

      <section className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <motion.div
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          className="lg:col-span-2 vercel-card overflow-hidden"
        >
          <div className="px-8 py-6 border-b vercel-border flex items-center justify-between bg-geist-gray-50">
            <div>
              <h2 className="text-xl font-bold tracking-tight">Recent Threat Intelligence</h2>
              <p className="text-sm text-geist-gray-500 mt-1">Last 5 binaries uploaded for analysis</p>
            </div>
            <button className="text-xs font-medium text-geist-gray-500 hover:text-black transition-colors uppercase tracking-widest">
              View All
            </button>
          </div>

          <div className="divide-y vercel-border">
            {recent.map((b, idx) => (
              <motion.div
                key={b.binaryId}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: idx * 0.05 }}
                className="px-8 py-4 flex items-center justify-between hover:bg-geist-gray-50 transition-colors group"
              >
                <div className="flex items-center gap-4">
                  <div className="w-10 h-10 rounded-vercel-md bg-geist-gray-100 flex items-center justify-center group-hover:bg-white transition-colors vercel-border">
                    <span className="text-xs font-mono font-bold">{b.format}</span>
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-black leading-none">{b.originalName}</p>
                    <p className="text-xs font-mono text-geist-gray-500 mt-2 lowercase">{b.sha256.slice(0, 32)}...</p>
                  </div>
                </div>
                <div className="flex items-center gap-4">
                  <span className="badge-vercel">
                    {formatBytes(b.sizeBytes)}
                  </span>
                  <button
                    onClick={() => onRunAnalysis(b, analysisProfile)}
                    disabled={!canRunAnalysis || !b.objectAvailable}
                    className="btn-vercel-secondary text-xs !px-3 !py-1 opacity-0 group-hover:opacity-100 transition-opacity disabled:opacity-40"
                    title={
                      !canRunAnalysis
                        ? "Requires ANALYST or ADMIN role"
                        : !b.objectAvailable
                          ? "Binary missing from storage. Re-upload to enable Run"
                          : `Run ${analysisProfile}`
                    }
                  >
                    Run Profile
                  </button>
                </div>
              </motion.div>
            ))}
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          className="vercel-card flex flex-col"
        >
          <div className="px-8 py-6 border-b vercel-border bg-geist-gray-50">
            <h2 className="text-xl font-bold tracking-tight">Analysis Engine</h2>
            <p className="text-sm text-geist-gray-500 mt-1">Configure global runner settings</p>
          </div>

          <div className="p-8 space-y-8 flex-1">
            <div className="space-y-4">
              <label className="text-xs font-mono uppercase tracking-widest text-geist-gray-500 block">Default Profile</label>
              <div className="grid grid-cols-1 gap-2">
                {["STATIC_BASELINE", "DYNAMIC_BASELINE"].map((p) => (
                  <button
                    key={p}
                    onClick={() => setAnalysisProfile(p)}
                    disabled={!canRunAnalysis}
                    className={`px-4 py-3 text-left rounded-vercel-md border transition-all ${
                      analysisProfile === p
                        ? "border-black bg-geist-gray-100 font-semibold"
                        : "border-geist-gray-100 hover:border-geist-gray-400"
                    } disabled:opacity-50`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-sm">{p.split("_")[0]} Analysis</span>
                      {analysisProfile === p && <ShieldCheck className="w-4 h-4 text-emerald-500" />}
                    </div>
                  </button>
                ))}
              </div>
            </div>

            <div className="p-4 rounded-vercel-md bg-develop bg-opacity-5 border border-develop border-opacity-10">
              <p className="text-xs text-develop leading-relaxed">
                <strong>Intelligence Node:</strong> Active and synchronized with the threat database. All runs will consume 1 credit per file.
              </p>
            </div>
          </div>

          <div className="p-8 bg-geist-gray-50 border-t vercel-border">
            <button className="btn-vercel-primary w-full flex items-center justify-center gap-2 group">
              <span>Sync All Data</span>
              <ArrowUpRight className="w-4 h-4 transition-transform group-hover:translate-x-1 group-hover:-translate-y-1" />
            </button>
          </div>
        </motion.div>
      </section>
    </div>
  );
}

const formatBytes = (b) => b < 1024 ? `${b} B` : b < 1024 * 1024 ? `${(b / 1024).toFixed(1)} KB` : `${(b / (1024 * 1024)).toFixed(1)} MB`;
