import { notFound } from "next/navigation";
import { obterSanto } from "@/lib/api";
import Chat from "./chat";
import { log } from "@/lib/log";

export const dynamic = "force-dynamic";

export default async function SantoPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  let santo;
  try { santo = await obterSanto(slug); }
  catch (e) {
    log.error(`página do santo "${slug}": backend indisponível: ${(e as Error).message}`);
    return <main><h1>Santos Vivos</h1><p className="erro">O servidor de conversa está indisponível no momento. Tente recarregar em instantes.</p><p><a href="/">← voltar</a></p></main>;
  }
  if (!santo) { log.warn(`página do santo "${slug}": não encontrado`); notFound(); }
  log.info(`página do santo "${slug}" aberta (${santo.ativo ? "ativo" : "em preparação"})`);
  return <Chat santo={santo} />;
}
