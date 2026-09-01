import type { Perfil } from "./types";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

const AUTH_KEY = "estoq_auth";

export function getAuth(): { token: string; nome: string; perfil: Perfil } | null {
  try {
    const raw = localStorage.getItem(AUTH_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function saveAuth(auth: { token: string; nome: string; perfil: Perfil }) {
  localStorage.setItem(AUTH_KEY, JSON.stringify(auth));
}

export function clearAuth() {
  localStorage.removeItem(AUTH_KEY);
}

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { ...(options.headers as Record<string, string>) };
  if (!(options.body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
  }
  const auth = getAuth();
  if (auth) {
    headers["Authorization"] = `Bearer ${auth.token}`;
  }

  const res = await fetch(url, { ...options, headers });

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  const data = text ? JSON.parse(text) : null;

  if (!res.ok) {
    if (res.status === 401 && !url.endsWith("/api/auth/login")) {
      clearAuth();
    }
    const message =
      data?.message || `Erro ${res.status} ao comunicar com o servidor.`;
    throw new ApiError(res.status, message);
  }
  return data as T;
}

export const api = {
  get: <T>(url: string) => request<T>(url),
  post: <T>(url: string, body?: unknown) =>
    request<T>(url, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(url: string, body?: unknown) =>
    request<T>(url, { method: "PUT", body: JSON.stringify(body) }),
  del: <T>(url: string) => request<T>(url, { method: "DELETE" }),

  upload: <T>(url: string, file: File, field = "arquivo") => {
    const form = new FormData();
    form.append(field, file);
    return request<T>(url, { method: "POST", body: form });
  },
};

export function formatMoney(v: number | null | undefined): string {
  if (v === null || v === undefined) return "—";
  return v.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

export function formatQtd(v: number | null | undefined): string {
  if (v === null || v === undefined) return "";
  return v.toLocaleString("pt-BR", { maximumFractionDigits: 3 });
}

export function formatPercent(v: number | null | undefined): string {
  if (v === null || v === undefined) return "—";
  return (v * 100).toLocaleString("pt-BR", { maximumFractionDigits: 2 }) + "%";
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const [y, m, d] = iso.split("T")[0].split("-");
  return `${d}/${m}/${y}`;
}