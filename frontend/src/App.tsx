import { NavLink, Navigate, Route, Routes } from "react-router-dom";
import { RequireAuth, useAuth } from "./auth";
import Produtos from "./pages/Produtos";
import Periodos from "./pages/Periodos";
import Compras from "./pages/Compras";
import Estoque from "./pages/Estoque";
import Relatorios from "./pages/Relatorios";
import Importar from "./pages/Importar";
import UsoDiario from "./pages/UsoDiario";
import Usuarios from "./pages/Usuarios";
import Login from "./pages/Login";

const navAdmin = [
  { to: "/relatorios", label: "Relatórios", ico: "📈" },
  { to: "/uso-diario", label: "Uso diário", ico: "📝" },
  { to: "/produtos", label: "Produtos", ico: "📦" },
  { to: "/periodos", label: "Períodos", ico: "🗓️" },
  { to: "/compras", label: "Compras", ico: "🛒" },
  { to: "/estoque", label: "Estoque", ico: "🏬" },
  { to: "/importar", label: "Importar planilha", ico: "🔄" },
  { to: "/usuarios", label: "Usuários", ico: "👤" },
];

export default function App() {
  const { auth, logout } = useAuth();

  if (!auth) {
    return (
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  const nav = auth.perfil === "COZINHA" ? navAdmin.filter((n) => n.to === "/uso-diario") : navAdmin;

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="logo">
          <img src="/img/logo-estoq.png" alt="EstoQ" className="logo-img-side" />
        </div>
        <nav>
          {nav.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.to === "/"} className={({ isActive }) => (isActive ? "active" : "")}>
              <span className="ico">{n.ico}</span>
              {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="side-foot">
          <div className="small" style={{ marginBottom: 8 }}>
            <strong>{auth.nome}</strong> · {auth.perfil === "ADMIN" ? "Administrador" : "Cozinha"}
          </div>
          <button className="btn small" onClick={() => logout()} style={{ width: "100%" }}>
            Sair
          </button>
        </div>
      </aside>
      <main className="main">
        <Routes>
          <Route path="/" element={<Navigate to="/relatorios" replace />} />
          <Route path="/uso-diario" element={<RequireAuth><UsoDiario /></RequireAuth>} />
          <Route path="/relatorios" element={<RequireAuth adminOnly><Relatorios /></RequireAuth>} />
          <Route path="/produtos" element={<RequireAuth adminOnly><Produtos /></RequireAuth>} />
          <Route path="/periodos" element={<RequireAuth adminOnly><Periodos /></RequireAuth>} />
          <Route path="/compras" element={<RequireAuth adminOnly><Compras /></RequireAuth>} />
          <Route path="/estoque" element={<RequireAuth adminOnly><Estoque /></RequireAuth>} />
          <Route path="/importar" element={<RequireAuth adminOnly><Importar /></RequireAuth>} />
          <Route path="/usuarios" element={<RequireAuth adminOnly><Usuarios /></RequireAuth>} />
          <Route path="*" element={<Navigate to="/relatorios" replace />} />
        </Routes>
      </main>
    </div>
  );
}