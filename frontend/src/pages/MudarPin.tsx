import { useState } from "react";
import { api } from "../api";
import { useAuth } from "../auth";
import { useToast } from "../ux";

export default function MudarPin() {
  const { logout } = useAuth();
  const { toast } = useToast();
  const [pin, setPin] = useState("");
  const [confirmacao, setConfirmacao] = useState("");
  const [erro, setErro] = useState("");
  const [salvando, setSalvando] = useState(false);

  async function salvar() {
    if (!/^\d{6}$/.test(pin)) {
      setErro("O novo PIN deve conter exatamente 6 dígitos.");
      return;
    }
    if (pin !== confirmacao) {
      setErro("A confirmação não confere com o novo PIN.");
      return;
    }
    setSalvando(true);
    setErro("");
    try {
      const me = await api.get<{ id: string }>("/api/auth/me");
      await api.put(`/api/usuarios/${me.id}/pin`, { pin });
      toast("PIN alterado com sucesso. Entre novamente com o novo PIN.", "ok");
      await logout();
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao salvar o PIN.");
      setSalvando(false);
    }
  }

  return (
    <div className="login-page">
      <div className="blob blob-a" aria-hidden="true" />
      <div className="blob blob-b" aria-hidden="true" />
      <div className="card" style={{ width: 400, maxWidth: "92vw" }}>
        <div className="card-pad">
          <div style={{ textAlign: "center", marginBottom: 16 }}>
            <h2 style={{ margin: 0 }}>Defina seu novo PIN</h2>
            <p className="muted small">
              Os usuários iniciais usam PIN padrão (000000). Troque por um PIN de 6 dígitos para manter
              o acesso seguro. Ao salvar, você será desconectado e entrará com o novo PIN.
            </p>
          </div>
          <div className="field">
            <label htmlFor="novo-pin">Novo PIN (6 dígitos)</label>
            <input
              id="novo-pin"
              type="password"
              inputMode="numeric"
              maxLength={6}
              value={pin}
              autoFocus
              onChange={(e) => setPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
            />
          </div>
          <div className="field">
            <label htmlFor="confirmar-pin">Confirmar novo PIN</label>
            <input
              id="confirmar-pin"
              type="password"
              inputMode="numeric"
              maxLength={6}
              value={confirmacao}
              onChange={(e) => setConfirmacao(e.target.value.replace(/\D/g, "").slice(0, 6))}
            />
          </div>
          {erro && <div className="form-error" role="alert">{erro}</div>}
          <div className="form-actions">
            <button className="btn primary" disabled={salvando || pin.length !== 6 || confirmacao.length !== 6} onClick={salvar}>
              {salvando ? "Salvando…" : "Salvar novo PIN"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}