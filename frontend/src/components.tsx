import React, { ReactNode } from "react";

export function Modal({
  title,
  onClose,
  children,
  wide,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  wide?: boolean;
}) {
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal${wide ? " wide" : ""}`}>
        <h3>{title}</h3>
        {children}
      </div>
    </div>
  );
}

export function Badge({ status }: { status: string }) {
  const map: Record<string, { cls: string; label: string }> = {
    ABERTO: { cls: "info", label: "Aberto" },
    FECHADO: { cls: "neutral", label: "Fechado" },
    OK: { cls: "ok", label: "OK" },
    ATENCAO: { cls: "warn", label: "Atenção" },
    REPOR: { cls: "danger", label: "Repor" },
    SEM_DADOS: { cls: "neutral", label: "Sem dados" },
  };
  const m = map[status] || { cls: "neutral", label: status };
  return <span className={`badge ${m.cls}`}>{m.label}</span>;
}

export function Stat({ label, value, hint, tone }: { label: string; value: ReactNode; hint?: string; tone?: "ok" | "warn" | "danger" }) {
  return (
    <div className="card stat">
      <div className="label">{label}</div>
      <div className="value" style={tone ? { color: `var(--${tone})` } : undefined}>{value}</div>
      {hint ? <div className="hint">{hint}</div> : null}
    </div>
  );
}

export function Gauge({ percent, label }: { percent: number; label: string }) {
  const pct = Math.max(0, Math.min(1, percent)) * 100;
  const r = 60;
  const circ = 2 * Math.PI * r;
  return (
    <div className="gauge">
      <div className="ring">
        <svg viewBox="0 0 150 150">
          <circle className="track" cx="75" cy="75" r={r} />
          <circle
            className="bar"
            cx="75"
            cy="75"
            r={r}
            strokeDasharray={circ}
            strokeDashoffset={circ - (circ * pct) / 100}
          />
        </svg>
      </div>
      <div style={{ fontSize: 26, fontWeight: 700 }}>{label}</div>
    </div>
  );
}

export function useAsyncData<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = React.useState<T | null>(null);
  const [error, setError] = React.useState("");
  const [loading, setLoading] = React.useState(true);
  const reload = React.useCallback(() => {
    setLoading(true);
    setError("");
    fn()
      .then(setData)
      .catch((e) => setError(e.message || "Erro ao carregar dados."))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  React.useEffect(reload, [reload]);
  return { data, error, loading, reload };
}