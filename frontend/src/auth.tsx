import React, { createContext, useCallback, useContext, useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { api, clearAuth, getAuth, saveAuth } from "./api";
import type { LoginResponse, Perfil } from "./types";

interface AuthState {
  token: string;
  nome: string;
  perfil: Perfil;
}

interface AuthContextValue {
  auth: AuthState | null;
  loading: boolean;
  login: (pin: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue>({ auth: null, loading: true, login: async () => {}, logout: async () => {} });

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [auth, setAuth] = useState<AuthState | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const saved = getAuth();
    if (saved) setAuth(saved);
    setLoading(false);
  }, []);

  const login = useCallback(async (pin: string) => {
    const r = await api.post<LoginResponse>("/api/auth/login", { pin });
    const novo = { token: r.token, nome: r.nome, perfil: r.perfil };
    saveAuth(novo);
    setAuth(novo);
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post("/api/auth/logout");
    } catch {
      // ignora falha de rede no logout
    }
    clearAuth();
    setAuth(null);
  }, []);

  return <AuthContext.Provider value={{ auth, loading, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}

export function RequireAuth({ children, adminOnly }: { children: React.ReactNode; adminOnly?: boolean }) {
  const { auth, loading } = useAuth();
  if (loading) return <div className="empty">Carregando…</div>;
  if (!auth) return <Navigate to="/login" replace />;
  if (auth.perfil === "COZINHA" && adminOnly) return <Navigate to="/movimentacoes" replace />;
  return <>{children}</>;
}

export function RequireCozinha({ children }: { children: React.ReactNode }) {
  const { auth, loading } = useAuth();
  if (loading) return <div className="empty">Carregando…</div>;
  if (!auth) return <Navigate to="/login" replace />;
  return <>{children}</>;
}