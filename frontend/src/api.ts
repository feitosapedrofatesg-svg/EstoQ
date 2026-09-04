import type { Page, Perfil, Produto } from "./types";

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

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const [dia, hora] = iso.split("T");
  const [y, m, d] = dia.split("-");
  return `${d}/${m}/${y} ${hora ? hora.slice(0, 5) : ""}`.trim();
}

export function hojeIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export function inicioMesIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-01`;
}

/** Obtém todas as páginas de produtos ativos, ordenados por nome. */
export async function carregarProdutos(): Promise<Produto[]> {
  const primeira = await api.get<Page<Produto>>("/api/produtos?page=0&size=500");
  let lista = [...primeira.content];
  for (let p = 1; p < primeira.totalPages; p++) {
    const mais = await api.get<Page<Produto>>(`/api/produtos?page=${p}&size=500`);
    lista = lista.concat(mais.content);
  }
  return lista.sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
}

/** Download de arquivo (ex.: CSV) autenticado, disparando o salvamento no navegador. */
export async function download(url: string, nome: string) {
  const auth = getAuth();
  const headers: Record<string, string> = {};
  if (auth) headers["Authorization"] = `Bearer ${auth.token}`;
  const res = await fetch(url, { headers });
  if (!res.ok) {
    let message = `Erro ${res.status} ao baixar o arquivo.`;
    try {
      const data = await res.json();
      message = data?.message || message;
    } catch {
      // corpo não é JSON — mantém a mensagem padrão
    }
    throw new ApiError(res.status, message);
  }
  const blob = await res.blob();
  const href = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = href;
  a.download = nome;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(href);
}