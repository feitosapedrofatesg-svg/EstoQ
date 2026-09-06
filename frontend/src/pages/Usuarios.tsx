import { useState } from "react";
import { api, formatDateTime } from "../api";
import { Modal, ErroCarregar, useAsyncData } from "../components";
import { useConfirm, useToast, TableSkeleton } from "../ux";
import type { Perfil, SessaoView, Usuario } from "../types";

const perfilInfo: Record<Perfil, { label: string; cls: string }> = {
  ADMIN: { label: "Administrador", cls: "info" },
  COZINHA: { label: "Cozinha", cls: "warn" },
  NUTRICIONISTA: { label: "Nutricionista", cls: "neutral" },
};

export default function Usuarios() {
  const { data, error, loading, reload } = useAsyncData<Usuario[]>(() => api.get("/api/usuarios"), []);
  const { data: sessoes, reload: reloadSessoes } = useAsyncData<SessaoView[]>(() => api.get("/api/auth/sessoes"), []);
  const [editando, setEditando] = useState<Usuario | null>(null);
  const [pin, setPin] = useState("");
  const [erro, setErro] = useState("");
  const [salvando, setSalvando] = useState(false);
  const [msgSessao, setMsgSessao] = useState("");
  const { confirmar } = useConfirm();
  const { toast } = useToast();

  async function revogarSessao(s: SessaoView) {
    const ok = await confirmar({
      titulo: "Encerrar sessão",
      texto: <>Encerrar a sessão de <strong>{s.usuarioNome}</strong>? {s.usuarioNome} precisará digitar o PIN novamente.</>,
      confirmarLabel: "Encerrar",
      perigo: true,
    });
    if (!ok) return;
    setMsgSessao("");
    try {
      await api.del(`/api/auth/sessoes/${s.id}`);
      reloadSessoes();
      toast(`Sessão encerrada: ${s.usuarioNome}`, "danger");
    } catch (e: unknown) {
      setMsgSessao((e as Error).message || "Erro ao encerrar a sessão.");
    }
  }

  if (loading) return (
    <div>
      <div className="page-head">
        <div><h1>Usuários</h1><p>Carregando…</p></div>
      </div>
      <TableSkeleton linhas={5} colunas={3} />
    </div>
  );
  if (error) return <ErroCarregar message={error} onTentar={reload} />;

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
            {(data || []).map((u) => {
              const info = perfilInfo[u.perfil] || { label: u.perfil, cls: "neutral" };
              return (
                <tr key={u.id}>
                  <td><strong>{u.nome}</strong></td>
                  <td>
                    <span className={`badge ${info.cls}`}>{info.label}</span>
                  </td>
                  <td>
                    <button className="btn small" onClick={() => abrir(u)}>Redefinir PIN</button>
                  </td>
                </tr>
              );
            })}
            {(data || []).length === 0 && (
              <tr><td colSpan={3} className="empty">Nenhum usuário encontrado.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="card card-pad" style={{ marginTop: 22 }}>
        <div className="filtro-line" style={{ marginBottom: 0 }}>
          <h3 style={{ margin: 0 }}>Sessões ativas</h3>
          <button className="btn small" onClick={reloadSessoes}>Atualizar</button>
        </div>
        {msgSessao && <div className="aviso danger" style={{ marginTop: 10 }}>{msgSessao}</div>}
        <div className="table-wrap" style={{ marginTop: 10 }}>
          <table className="tbl">
            <thead>
              <tr>
                <th>Usuário</th>
                <th>Login</th>
                <th>Expira</th>
                <th>Origem</th>
                <th style={{ width: 120 }}>Ações</th>
              </tr>
            </thead>
            <tbody>
              {(sessoes || []).map((s) => (
                <tr key={s.id}>
                  <td><strong>{s.usuarioNome}</strong></td>
                  <td>{formatDateTime(s.criadoEm)}</td>
                  <td>{formatDateTime(s.expiraEm)}</td>
                  <td className="small muted">{s.origem || "—"}</td>
                  <td>
                    <button className="btn small danger" onClick={() => revogarSessao(s)}>Encerrar</button>
                  </td>
                </tr>
              ))}
              {(sessoes || []).length === 0 && (
                <tr><td colSpan={5} className="empty">Nenhuma sessão ativa.</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <p className="small muted" style={{ marginBottom: 0 }}>
          Login bloqueado por 15 minutos após 5 tentativas erradas de PIN. Sessões expiram automaticamente em 24 horas.
        </p>
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