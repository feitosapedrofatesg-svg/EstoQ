import { NavLink, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { motion } from "motion/react";
import { RequireAuth, useAuth } from "./auth";
import Produtos from "./pages/Produtos";
import Relatorios from "./pages/Relatorios";
import Movimentacoes from "./pages/Movimentacoes";
import Desperdicio from "./pages/Desperdicio";
import Lotes from "./pages/Lotes";
import Conferencia from "./pages/Conferencia";
import Usuarios from "./pages/Usuarios";
import Login from "./pages/Login";

const navAdmin = [
  { to: "/relatorios", label: "Relatórios", ico: "📈" },
  { to: "/movimentacoes", label: "Movimentações", ico: "📝" },
  { to: "/desperdicio", label: "Desperdício", ico: "✂️" },
  { to: "/lotes", label: "Estoque", ico: "📦" },
  { to: "/produtos", label: "Produtos", ico: "🏷️" },
  { to: "/conferencia", label: "Conferência", ico: "🔍" },
  { to: "/usuarios", label: "Usuários", ico: "👤" },
];

const navCozinha = [
  { to: "/movimentacoes", label: "Movimentações", ico: "📝" },
  { to: "/lotes", label: "Estoque", ico: "📦" },
  { to: "/desperdicio", label: "Desperdício", ico: "✂️" },
];

const navNutricionista = [
  { to: "/relatorios", label: "Relatórios", ico: "📈" },
  { to: "/produtos", label: "Produtos", ico: "🏷️" },
  { to: "/lotes", label: "Estoque", ico: "📦" },
];

const perfilLabel: Record<string, string> = {
  ADMIN: "Administrador",
  COZINHA: "Cozinha",
  NUTRICIONISTA: "Nutricionista",
};

export default function App() {
  const { auth, logout } = useAuth();
  const location = useLocation();

  if (!auth) {
    return (
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  const ehCozinha = auth.perfil === "COZINHA";
  const ehNutricionista = auth.perfil === "NUTRICIONISTA";
  const home = ehCozinha ? "/movimentacoes" : "/relatorios";
  const nav = ehCozinha ? navCozinha : ehNutricionista ? navNutricionista : navAdmin;

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
            <strong>{auth.nome}</strong> · {perfilLabel[auth.perfil] || auth.perfil}
          </div>
          <button className="btn small" onClick={() => logout()} style={{ width: "100%" }}>
            Sair
          </button>
        </div>
      </aside>
      <main className="main">
        <motion.div
          key={location.pathname}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.28, ease: "easeOut" }}
        >
          <Routes location={location}>
            <Route path="/" element={<Navigate to={home} replace />} />
            <Route path="/movimentacoes" element={<RequireAuth><Movimentacoes /></RequireAuth>} />
            <Route path="/produtos" element={<RequireAuth><Produtos /></RequireAuth>} />
            <Route path="/lotes" element={<RequireAuth><Lotes /></RequireAuth>} />
            <Route path="/relatorios" element={<RequireAuth adminOnly><Relatorios /></RequireAuth>} />
            <Route path="/desperdicio" element={<RequireAuth><Desperdicio /></RequireAuth>} />
            <Route path="/conferencia" element={<RequireAuth adminOnly><Conferencia /></RequireAuth>} />
            <Route path="/usuarios" element={<RequireAuth adminOnly><Usuarios /></RequireAuth>} />
            <Route path="*" element={<Navigate to={home} replace />} />
          </Routes>
        </motion.div>
      </main>
    </div>
  );
}