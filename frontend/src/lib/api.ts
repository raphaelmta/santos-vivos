export type Santo = {
  slug: string; nome: string; nomeCurto: string; subtitulo: string; descricao: string; ativo: boolean;
  fotoUrl: string | null; fotoCredito: string | null; fidelidadeVoz: string | null;
};
export type Msg = { role: "user" | "assistant"; content: string };
export type Fonte = { n: number; titulo: string; secao: string; tipo: string; texto: string; original: string | null; url: string; licenca: string | null };
export type Resposta = {
  resposta: string; fontes: Fonte[]; categoria: string; veredito: string; motivoVeredito: string;
  tentativas: number; segundos: number; cache: boolean;
};

/** Erro já com mensagem amigável para exibir ao usuário; `detalhe` vai só para o console. */
export class ApiError extends Error {
  constructor(public amigavel: string, public detalhe: string, public status: number) { super(amigavel); }
}

// No servidor (SSR) falamos direto com o backend; no navegador passamos pelo proxy /api do próprio Next.
const BASE = typeof window === "undefined" ? (process.env.API_URL ?? "http://localhost:8080") : "";

async function lerErro(r: Response): Promise<ApiError> {
  let j: { erro?: string; detalhe?: string } = {};
  try { j = await r.json(); } catch { /* sem corpo JSON */ }
  return new ApiError(j.erro ?? "O servidor não conseguiu responder agora. Tente de novo em instantes.", j.detalhe ?? `HTTP ${r.status}`, r.status);
}

export async function listarSantos(): Promise<Santo[]> {
  const r = await fetch(`${BASE}/api/santos`, { cache: "no-store" });
  if (!r.ok) throw await lerErro(r);
  return r.json();
}

export async function obterSanto(slug: string): Promise<Santo | null> {
  const r = await fetch(`${BASE}/api/santos/${slug}`, { cache: "no-store" });
  if (r.status === 404) return null;
  if (!r.ok) throw await lerErro(r);
  return r.json();
}

export async function conversar(slug: string, historico: Msg[], pergunta: string): Promise<Resposta> {
  let r: Response;
  try {
    r = await fetch(`/api/santos/${slug}/chat`, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ historico, pergunta }),
    });
  } catch (e) {
    throw new ApiError("Sem conexão com o servidor. Verifique sua internet e tente de novo.", String(e), 0);
  }
  if (!r.ok) throw await lerErro(r);
  return r.json();
}
