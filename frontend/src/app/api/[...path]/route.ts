import { NextRequest, NextResponse } from "next/server";
import { log } from "@/lib/log";

/**
 * Proxy de /api/* para o backend Java. Substitui o rewrite do Next, que derrubava a conexão
 * ("socket hang up") em respostas longas. Aqui controlamos timeout, logs e formato de erro.
 */
const BACKEND = process.env.API_URL ?? "http://localhost:8080";
const TIMEOUT_MS = Number(process.env.API_TIMEOUT_MS ?? 180_000);

export const dynamic = "force-dynamic";

type Ctx = { params: Promise<{ path: string[] }> };

async function proxy(req: NextRequest, ctx: Ctx) {
  const { path } = await ctx.params;
  const alvo = `${BACKEND}/api/${path.join("/")}${req.nextUrl.search}`;
  const inicio = Date.now();
  const id = Math.random().toString(36).slice(2, 8);
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);

  let corpo: string | undefined;
  let resumo = "";
  if (req.method === "POST") {
    corpo = await req.text();
    try {
      const j = JSON.parse(corpo);
      if (typeof j.pergunta === "string") resumo = ` pergunta="${j.pergunta.slice(0, 80)}"${j.pergunta.length > 80 ? "…" : ""} historico=${(j.historico ?? []).length}`;
    } catch { /* corpo não é JSON: segue sem resumo */ }
  }
  log.info(`[${id}] → ${req.method} /api/${path.join("/")}${resumo}`);

  try {
    const r = await fetch(alvo, {
      method: req.method,
      headers: corpo !== undefined ? { "Content-Type": "application/json" } : undefined,
      body: corpo,
      signal: ctrl.signal,
      cache: "no-store",
    });
    const ms = Date.now() - inicio;
    const tipo = r.headers.get("content-type") ?? "";

    if (tipo.startsWith("image/")) {
      log.info(`[${id}] ← ${r.status} imagem ${tipo} em ${ms}ms`);
      return new NextResponse(await r.arrayBuffer(), { status: r.status, headers: { "Content-Type": tipo, "Cache-Control": "public, max-age=86400" } });
    }

    const texto = await r.text();
    let json: Record<string, unknown> | null = null;
    try { json = JSON.parse(texto); } catch { /* backend respondeu algo que não é JSON */ }

    if (!r.ok) {
      const erro = (json?.erro as string) ?? texto.slice(0, 200) ?? `HTTP ${r.status}`;
      log.warn(`[${id}] ← ${r.status} em ${ms}ms: ${erro}`);
      return NextResponse.json({ erro: mensagemAmigavel(r.status, erro), detalhe: erro, status: r.status }, { status: r.status });
    }
    if (json && "resposta" in json) {
      const u = (json.uso as Record<string, number> | undefined) ?? {};
      log.info(`[${id}] ← 200 chat em ${ms}ms | categoria=${json.categoria} veredito=${json.veredito} tentativas=${json.tentativas} cache=${json.cache} fontes=${(json.fontes as unknown[])?.length ?? 0} tokens_in=${u.input ?? "-"} tokens_out=${u.output ?? "-"}`);
    } else {
      log.info(`[${id}] ← ${r.status} em ${ms}ms (${texto.length} bytes)`);
    }
    return new NextResponse(texto, { status: r.status, headers: { "Content-Type": "application/json; charset=utf-8" } });
  } catch (e) {
    const ms = Date.now() - inicio;
    const err = e as Error & { cause?: { code?: string } };
    const codigo = err.name === "AbortError" ? "TIMEOUT" : err.cause?.code ?? err.name;
    log.error(`[${id}] ✖ falha ao falar com o backend (${alvo}) após ${ms}ms: ${codigo} ${err.message}`);
    const amigavel = codigo === "TIMEOUT"
      ? "A resposta demorou demais. Tente de novo em instantes."
      : codigo === "ECONNREFUSED" || codigo === "ECONNRESET"
        ? "O servidor de conversa está fora do ar ou reiniciando. Tente de novo em alguns segundos."
        : "Não consegui falar com o servidor de conversa. Tente de novo.";
    return NextResponse.json({ erro: amigavel, detalhe: `${codigo}: ${err.message}`, status: 503 }, { status: 503 });
  } finally {
    clearTimeout(timer);
  }
}

function mensagemAmigavel(status: number, detalhe: string) {
  if (status === 404) return "Esse santo não está disponível.";
  if (status === 409) return "Esse santo ainda está em preparação.";
  if (status === 400) return "Não entendi a pergunta. Escreva algo e tente de novo.";
  if (status === 429) return "Muitas perguntas em pouco tempo. Aguarde um instante.";
  return "O servidor não conseguiu responder agora. Tente de novo em instantes.";
}

export const GET = proxy;
export const POST = proxy;
