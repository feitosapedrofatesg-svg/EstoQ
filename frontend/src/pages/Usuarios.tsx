import { useState } from "react";
import { api } from "../api";
import { Modal, useAsyncData } from "../components";
import type { Usuario } from "../types";

export default function Usuarios() {
  const { data, error, loading, reload } = useAsyncData<Usuario[]>(() => api.get("/api/usuarios"), []);
  const [editando, setEditando] = useState<Usuario | null>(null);
  const [pin, setPin] = useState("");
  const [erro, setErro] = useState("");
  const [salvando, setSalvando] = useState(false);

  if (loading) return <div className="muted">Carregando…</div>;
  if (error) return <div className="empty">Erro: {error}</div>;

  function abrir(u: Usuario) {
    setEditando(u);
    setPin("");
    setErro("");
  }

  async function salvar() {
    if (!editando || !/^\d{6}$/.test(pin)) {
      setErro("O novo PIN deve conter exatamente 6 dígitos.");
      return;
    }
    setSalvando(true);
    setErro("");
    try {
      await api.put(`/api/usuarios/${editando.id}/pin`, { pin });
      setEditando(null);
      reload();
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao salvar.");
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Usuários</h1>
          <p>Gerencie os PINs de acesso (todos com 6 dígitos)</p>
        </div>
      </div>

      <div className="table-wrap">
        <table className="tbl">
          <thead>
            <tr>
              <th>Nome</th>
              <th>Perfil</th>
              <th style={{ width: 160 }}>Ações</th>
            </tr>
          </thead>
          <tbody>
            {(data || []).map((u) => (
              <tr key={u.id}>
                <td><strong>{u.nome}</strong></td>
                <td>
                  <span className={`badge ${u.perfil === "ADMIN" ? "info" : "warn"}`}>
                    {u.perfil === "ADMIN" ? "Administrador" : "Cozinha"}
                  </span>
                </td>
                <td>
                  <button className="btn small" onClick={() => abrir(u)}>Redefinir PIN</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editando && (
        <Modal title={`Redefinir PIN — ${editando.nome}`} onClose={() => setEditando(null)}>
          <div className="field">
            <label>Novo PIN (6 dígitos)</label>
            <input
              type="password"
              inputMode="numeric"
              maxLength={6}
              value={pin}
              autoFocus
              onChange={(e) => setPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
            />
          </div>
          {erro && <div className="form-error">{erro}</div>}
          <div className="form-actions">
            <button className="btn" onClick={() => setEditando(null)}>Cancelar</button>
            <button className="btn primary" disabled={salvando || pin.length !== 6} onClick={salvar}>
              {salvando ? "Salvando…" : "Salvar novo PIN"}
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}