import { motion, useReducedMotion, type Variants } from "motion/react";
import { useMemo, useRef, useState } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../auth";

const keypadVariants: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.035, delayChildren: 0.18 } },
};
const keyVariants: Variants = {
  hidden: { opacity: 0, y: 8 },
  show: { opacity: 1, y: 0 },
};

const FOODS = ["🍅", "🥕", "🥦", "🍋", "🍎", "🥑", "🌽", "🍇", "🥕", "🍞", "🧄", "🥚", "🍊", "🫑", "🍉"];
const WIDTH = 100;

function Rain() {
  const reduce = useReducedMotion();
  const items = useMemo(
    () =>
      Array.from({ length: 14 }, (_, i) => {
        const left = Math.random() * WIDTH;
        const size = 14 + Math.random() * 20;
        const duration = 9 + Math.random() * 9;
        const delay = Math.random() * 14;
        const spin = Math.random() * 360;
        return {
          id: i,
          left,
          size,
          duration,
          delay,
          spin,
          emoji: FOODS[Math.floor(Math.random() * FOODS.length)],
        };
      }),
    []
  );

  if (reduce) return null;

  return (
    <div className="rain" aria-hidden="true">
      {items.map((it) => (
        <motion.span
          key={it.id}
          className="rain-item"
          style={{
            left: `${it.left}%`,
            fontSize: it.size,
            ["--spin" as string]: `${it.spin}deg`,
          }}
          initial={{ y: "-12vh", x: 0, opacity: 0 }}
          animate={{
            y: ["-12vh", "110vh"],
            x: [0, 34, -30, 18, 0],
            opacity: [0, 1, 1, 1, 0.9],
            rotate: [0, 180, 360],
          }}
          transition={{
            duration: it.duration,
            delay: it.delay,
            repeat: Infinity,
            ease: "linear",
          }}
        >
          {it.emoji}
        </motion.span>
      ))}
    </div>
  );
}

export default function Login() {
  const { auth, login } = useAuth();
  const [pin, setPin] = useState("");
  const [erro, setErro] = useState("");
  const [enviando, setEnviando] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const reduce = useReducedMotion();

  if (auth) {
    return <Navigate to={auth.perfil === "COZINHA" ? "/consumo" : "/"} replace />;
  }

  async function autenticar(valor: string) {
    if (valor.length !== 6 || enviando) return;
    setEnviando(true);
    setErro("");
    try {
      await login(valor);
    } catch (e: unknown) {
      setErro((e as Error).message || "PIN incorreto.");
      setPin("");
      inputRef.current?.focus();
    } finally {
      setEnviando(false);
    }
  }

  function digitar(d: string) {
    setErro("");
    const novo = (pin + d).slice(0, 6);
    setPin(novo);
    if (novo.length === 6) autenticar(novo);
  }

  function apagar() {
    setErro("");
    setPin((p) => p.slice(0, -1));
  }

  return (
    <div className="login-page">
      <div className="blob blob-a" aria-hidden="true" />
      <div className="blob blob-b" aria-hidden="true" />
      <Rain />

      <motion.form
        className="login-card"
        role="form"
        aria-label="Login com PIN"
        onSubmit={(e) => {
          e.preventDefault();
          autenticar(pin);
        }}
        initial={reduce ? { opacity: 0 } : { opacity: 0, y: 18, scale: 0.98 }}
        animate={reduce ? { opacity: 1 } : { opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.45, ease: "easeOut" }}
      >
        <motion.div
          className="login-logo"
          initial={reduce ? undefined : { opacity: 0, y: 10 }}
          animate={reduce ? undefined : { opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.08, ease: "easeOut" }}
        >
          <img src="/img/logo-estoq.png" alt="EstoQ — Gestão Inteligente de Estoque e Custos para Restaurantes" className="logo-img" />
          <p>Digite o PIN de 6 dígitos para entrar</p>
        </motion.div>

        <input
          ref={inputRef}
          type="password"
          inputMode="numeric"
          autoComplete="off"
          autoFocus
          className="pin-hidden"
          aria-label="PIN de acesso"
          value={pin}
          onChange={(e) => {
            const v = e.target.value.replace(/\D/g, "").slice(0, 6);
            setPin(v);
            setErro("");
            if (v.length === 6) autenticar(v);
          }}
        />

        <div className="pin-dots" aria-hidden="true">
          {[0, 1, 2, 3, 4, 5].map((i) => {
            const filled = pin.length > i;
            return (
              <motion.span
                key={i}
                className={`dot${filled ? " filled" : ""}`}
                animate={filled ? { scale: [1, 1.22, 1] } : { scale: 1 }}
                transition={{ duration: 0.2 }}
              >
                {filled ? "•" : ""}
              </motion.span>
            );
          })}
        </div>
        <span className="sr-only" aria-live="polite">{pin.length}/6 dígitos</span>

        {erro && (
          <motion.div
            className="login-error"
            role="alert"
            initial={{ opacity: 0, y: -6 }}
            animate={reduce ? { opacity: 1 } : { opacity: 1, y: 0, x: [0, -8, 8, -5, 5, -2, 2, 0] }}
            transition={{ duration: 0.45 }}
          >
            {erro}
          </motion.div>
        )}

        {enviando ? (
          <div className="pin-check" role="status">
            <span className="spinner" aria-hidden="true" /> Entrando…
          </div>
        ) : (
          <motion.div
            className="keypad"
            variants={keypadVariants}
            initial="hidden"
            animate="show"
          >
            {["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"].map((k, i) =>
              k === "" ? (
                <div key={i} aria-hidden="true" />
              ) : k === "⌫" ? (
                <motion.button
                  key={i}
                  type="button"
                  className="key"
                  onClick={apagar}
                  aria-label="Apagar um dígito"
                  variants={keyVariants}
                  whileTap={reduce ? undefined : { scale: 0.93 }}
                >
                  ⌫
                </motion.button>
              ) : (
                <motion.button
                  key={i}
                  type="button"
                  className="key"
                  onClick={() => digitar(k)}
                  variants={keyVariants}
                  whileTap={reduce ? undefined : { scale: 0.93 }}
                >
                  {k}
                </motion.button>
              )
            )}
          </motion.div>
        )}

        <p className="login-hint">Admin: 000000 · Cozinha: 111111</p>
      </motion.form>
    </div>
  );
}