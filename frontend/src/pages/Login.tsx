import { useRef, useState } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../auth";

export default function Login() {
  const { auth, login } = useAuth();
  const [pin, setPin] = useState("");
  const [erro, setErro] = useState("");
  const [enviando, setEnviando] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  if (auth) {
    return <Navigate to={auth.perfil === "COZINHA" ? "/uso-diario" : "/"} replace />;
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
      <div className="login-card">
        <div className="login-logo">
          <img src="/img/logo-estoq.png" alt="EstoQ — Gestão Inteligente de Estoque e Custos para Restaurantes" className="logo-img" />
          <p>Digite o PIN de 6 dígitos para entrar</p>
        </div>

        <input
          ref={inputRef}
          type="password"
          inputMode="numeric"
          autoFocus
          className="pin-hidden"
          value={pin}
          onChange={(e) => {
            const v = e.target.value.replace(/\D/g, "").slice(0, 6);
            setPin(v);
            setErro("");
            if (v.length === 6) autenticar(v);
          }}
        />

        <div className="pin-dots">
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <span key={i} className={`dot${pin.length > i ? " filled" : ""}`}>
              {i < pin.length ? "•" : ""}
            </span>
          ))}
        </div>

        {erro && <div className="login-error">{erro}</div>}

        {enviando ? (
          <div className="pin-check">Entrando…</div>
        ) : (
          <div className="keypad">
            {["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"].map((k, i) =>
              k === "" ? (
                <div key={i} />
              ) : k === "⌫" ? (
                <button key={i} className="key" onClick={apagar} aria-label="Apagar">
                  ⌫
                </button>
              ) : (
                <button key={i} className="key" onClick={() => digitar(k)}>
                  {k}
                </button>
              )
            )}
          </div>
        )}

        <p className="login-hint">Admin: 000000 · Cozinha: 111111</p>
      </div>
    </div>
  );
}