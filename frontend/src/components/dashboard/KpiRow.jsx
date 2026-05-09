export function KpiRow({ binariesCount, healthStatus, roles }) {
  return (
    <section className="kpi-row">
      <article className="kpi-card">
        <span>Total binaries</span>
        <strong>{binariesCount}</strong>
      </article>
      <article className="kpi-card">
        <span>Backend status</span>
        <strong>{healthStatus || "UNKNOWN"}</strong>
      </article>
      <article className="kpi-card">
        <span>Active roles</span>
        <strong>{roles.join(", ") || "None"}</strong>
      </article>
    </section>
  );
}
