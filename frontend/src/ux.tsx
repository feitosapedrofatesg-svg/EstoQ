import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import type { Produto } from "./types";

/* ============================================================
 * Toasts — feedback global de sucesso/erro/info (H1, H9)
 * ============================================================ */

type ToastTipo = "ok" | "danger" | "info";

interface ToastItem {
  id: number;
  msg: string;
  tipo: ToastTipo;
}

interface ToastContextValue {
  toast: (msg: string, tipo?: ToastTipo, duracaoMs?: number) => void;
}

const ToastContext = createContext<ToastContextValue>({ toast: () => {} });

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(0);

  const toast = useCallback<ToastContextValue["toast"]>((msg, tipo = "ok", duracaoMs = 4200) => {
    const id = ++nextId.current;
    setItems((t) => [...t.slice(-3), { id, msg, tipo }]);
    window.setTimeout(() => setItems((t) => t.filter((x) => x.id !== id)), duracaoMs);
  }, []);

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      <div className="toast-wrap" role="status" aria-live="polite">
        <AnimatePresence>
          {items.map((t) => (
            <motion.div
              key={t.id}
              className={`toast ${t.tipo}`}
              role={t.tipo === "danger" ? "alert" : "status"}
              initial={{ opacity: 0, x: 28, scale: 0.96 }}
              animate={{ opacity: 1, x: 0, scale: 1 }}
              exit={{ opacity: 0, x: 28, scale: 0.96 }}
              transition={{ duration: 0.22, ease: "easeOut" }}
              onClick={() => setItems((t2) => t2.filter((x) => x.id !== t.id))}
            >
              <span className="toast-ico" aria-hidden="true">
                {t.tipo === "ok" ? "✓" : t.tipo === "danger" ? "!" : "i"}
              </span>
              <span>{t.msg}</span>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}

/* ============================================================
 * ConfirmDialog — substitui window.confirm (H3)
 * ============================================================ */

export interface ConfirmOpts {
  texto: React.ReactNode;
  titulo?: string;
  confirmarLabel?: string;
  cancelarLabel?: string;
  /** true → botão vermelho (ação destrutiva) */
  perigo?: boolean;
}

interface ConfirmState {
  opts: ConfirmOpts;
  resolver: (ok: boolean) => void;
}

interface ConfirmContextValue {
  confirmar: (opts: ConfirmOpts) => Promise<boolean>;
}

const ConfirmContext = createContext<ConfirmContextValue>({ confirmar: async () => false });

export function ConfirmProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<ConfirmState | null>(null);
  const reduce = useReducedMotion();

  const confirmar = useCallback<ConfirmContextValue["confirmar"]>((opts) => {
    return new Promise<boolean>((resolver) => setState({ opts, resolver }));
  }, []);

  const fechar = useCallback(
    (ok: boolean) => {
      if (state) state.resolver(ok);
      setState(null);
    },
    [state]
  );

  useEffect(() => {
    function onKey(ev: KeyboardEvent) {
      if (ev.key === "Escape") fechar(false);
    }
    if (state) window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [state, fechar]);

  return (
    <ConfirmContext.Provider value={{ confirmar }}>
      {children}
      <AnimatePresence>
        {state && (
          <motion.div
            className="modal-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onMouseDown={(e) => e.target === e.currentTarget && fechar(false)}
          >
            <motion.div
              className="modal confirm-modal"
              role="alertdialog"
              aria-modal="true"
              aria-labelledby="confirm-titulo"
              initial={reduce ? { opacity: 0 } : { opacity: 0, scale: 0.96, y: 12 }}
              animate={reduce ? { opacity: 1 } : { opacity: 1, scale: 1, y: 0 }}
              exit={reduce ? { opacity: 0 } : { opacity: 0, scale: 0.96, y: 8 }}
              transition={{ duration: 0.18, ease: "easeOut" }}
            >
              <h3 id="confirm-titulo">{state.opts.titulo || "Confirmação"}</h3>
              <div className="confirm-text">{state.opts.texto}</div>
              <div className="form-actions">
                <button className="btn" onClick={() => fechar(false)}>
                  {state.opts.cancelarLabel || "Cancelar"}
                </button>
                <button
                  className={`btn ${state.opts.perigo ? "danger" : "primary"}`}
                  autoFocus
                  onClick={() => fechar(true)}
                >
                  {state.opts.confirmarLabel || "Confirmar"}
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </ConfirmContext.Provider>
  );
}

export function useConfirm() {
  return useContext(ConfirmContext);
}

/* ============================================================
 * Skeleton — carregamento fluido (H1)
 * ============================================================ */

export function Skeleton({
  w,
  h = 14,
  br = 8,
  className,
}: {
  w?: number | string;
  h?: number | string;
  br?: number;
  className?: string;
}) {
  return <span className={`skel ${className || ""}`} style={{ width: w ?? "100%", height: h, borderRadius: br }} />;
}

export function TableSkeleton({ linhas = 6, colunas = 6 }: { linhas?: number; colunas?: number }) {
  return (
    <div className="table-wrap" aria-busy="true">
      <table className="tbl">
        <tbody>
          {Array.from({ length: linhas }).map((_, r) => (
            <tr key={r}>
              {Array.from({ length: colunas }).map((_, c) => (
                <td key={c}>
                  <Skeleton w={`${55 + ((r + c) % 3) * 14}%`} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function CardsSkeleton({ n = 4 }: { n?: number }) {
  return (
    <div className="grid cards" aria-busy="true">
      {Array.from({ length: n }).map((_, i) => (
        <div className="card stat" key={i}>
          <Skeleton w="40%" h={12} />
          <div style={{ marginTop: 10 }}>
            <Skeleton w="70%" h={26} br={8} />
          </div>
          <div style={{ marginTop: 8 }}>
            <Skeleton w="55%" h={12} />
          </div>
        </div>
      ))}
    </div>
  );
}

export function EmptyState({ children }: { children?: React.ReactNode }) {
  return (
    <div className="empty-wrap">
      <div className="empty-ico" aria-hidden="true">📭</div>
      <div className="empty">{children || "Nenhum registro encontrado."}</div>
    </div>
  );
}

/* ============================================================
 * CountUp — anima números nos dashboards (H8)
 * ============================================================ */

export function CountUp({
  value,
  format = (n: number) => String(n),
  dur = 650,
}: {
  value: number;
  format?: (n: number) => string;
  dur?: number;
}) {
  const [display, setDisplay] = useState(value);
  const prev = useRef(value);
  const reduce = useReducedMotion();

  useEffect(() => {
    if (reduce || value === prev.current) {
      setDisplay(value);
      prev.current = value;
      return;
    }
    const from = prev.current;
    const start = performance.now();
    let raf = 0;
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / dur);
      const eased = 1 - Math.pow(1 - t, 3);
      setDisplay(from + (value - from) * eased);
      if (t < 1) {
        raf = requestAnimationFrame(tick);
      } else {
        prev.current = value;
      }
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [value, dur, reduce]);

  return <>{format(display)}</>;
}

/* ============================================================
 * ProductPicker — busca com digitação entre os produtos (H6/H7)
 * ============================================================ */

export function ProductPicker({
  produtos,
  value,
  onChange,
  placeholder = "Buscar produto…",
  autoFocus,
}: {
  produtos: Produto[];
  value: string;
  onChange: (id: string) => void;
  placeholder?: string;
  autoFocus?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState("");
  const boxRef = useRef<HTMLDivElement>(null);
  const reduce = useReducedMotion();

  const selecionado = useMemo(() => produtos.find((p) => p.id === value), [produtos, value]);

  useEffect(() => {
    function onDoc(ev: MouseEvent) {
      if (boxRef.current && !boxRef.current.contains(ev.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, []);

  const filtrados = useMemo(() => {
    if (!q.trim()) return produtos.slice(0, 60);
    const t = q.trim().toLowerCase();
    const acentos = t
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "");
    const lista = produtos.filter((p) => {
      const nome = p.nome.toLowerCase();
      const semAcento = nome.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
      return nome.includes(t) || semAcento.includes(acentos) || p.nome.toLowerCase().includes(t);
    });
    return lista.slice(0, 60);
  }, [produtos, q]);

  function selecionar(p: Produto) {
    onChange(p.id);
    setOpen(false);
    setQ("");
  }

  return (
    <div className="ppick" ref={boxRef}>
      <input
        className="ppick-input"
        value={open ? q : selecionado?.nome || ""}
        placeholder={placeholder}
        autoFocus={autoFocus}
        autoComplete="off"
        role="combobox"
        aria-expanded={open}
        aria-label={placeholder}
        onFocus={() => setOpen(true)}
        onChange={(e) => {
          setQ(e.target.value);
          setOpen(true);
          if (!e.target.value) onChange("");
        }}
      />
      {selecionado && !open && (
        <button
          type="button"
          className="ppick-clear"
          aria-label="Limpar produto"
          onClick={() => {
            onChange("");
            setQ("");
          }}
        >
          ✕
        </button>
      )}
      <AnimatePresence>
        {open && (
          <motion.div
            className="ppick-list"
            initial={reduce ? { opacity: 0 } : { opacity: 0, y: -4 }}
            animate={reduce ? { opacity: 1 } : { opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.15 }}
          >
            {filtrados.length === 0 ? (
              <div className="ppick-vazio">Nenhum produto encontrado.</div>
            ) : (
              filtrados.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  className={`ppick-item${p.id === value ? " sel" : ""}`}
                  onClick={() => selecionar(p)}
                >
                  <span className="ppick-nome">{p.nome}</span>
                  <span className="ppick-un">{p.unidadeMedida}</span>
                </button>
              ))
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

/* ============================================================
 * QuantityInput — stepper de quantidade
 * ============================================================ */

const IQS = (v: string, min: number) => {
  const n = parseFloat(v.replace(",", "."));
  return Number.isNaN(n) ? min : n;
};

export function QuantityInput({
  value,
  onChange,
  min = 0,
  step = 1,
  unidade,
  style,
}: {
  value: string;
  onChange: (v: string) => void;
  min?: number;
  step?: number;
  unidade?: string;
  style?: React.CSSProperties;
}) {
  function ajustar(delta: number) {
    const atual = IQS(value, min);
    const novo = Math.max(min, Math.round((atual + delta) * 1000) / 1000);
    onChange(Number.isInteger(novo) ? String(novo) : novo.toFixed(3).replace(/\.?0+$/, ""));
  }

  return (
    <div className="qty-stepper" style={style}>
      <button type="button" className="qty-btn" aria-label="Diminuir" onClick={() => ajustar(-step)}>
        −
      </button>
      <input
        type="number"
        inputMode="decimal"
        min={min}
        step={step}
        value={value}
        aria-label="Quantidade"
        onChange={(e) => onChange(e.target.value)}
      />
      <button type="button" className="qty-btn" aria-label="Aumentar" onClick={() => ajustar(step)}>
        +
      </button>
      {unidade && <span className="qty-un">{unidade}</span>}
    </div>
  );
}