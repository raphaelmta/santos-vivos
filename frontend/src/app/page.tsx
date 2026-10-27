import Link from "next/link";
import { listarSantos, type Santo } from "@/lib/api";
import { log } from "@/lib/log";

export const dynamic = "force-dynamic";

function Card({ s }: { s: Santo }) {
  const inner = (
    <>
      {s.fotoUrl ? <img className="foto" src={s.fotoUrl} alt={s.nome} /> : <div className="semfoto">✝</div>}
      <div className="corpo">
        <div className="tag">{s.ativo ? "Disponível" : "Em preparação"}</div>
        <h2>{s.nome}</h2>
        <div className="sub" style={{ margin: 0 }}>{s.subtitulo}</div>
        <p>{s.descricao}</p>
      </div>
    </>
  );
  return s.ativo
    ? <Link className="card" href={`/santo/${s.slug}`}>{inner}</Link>
    : <div className="card inativo" aria-disabled>{inner}</div>;
}

export default async function Home() {
  let santos: Santo[] = [];
  let erro: string | null = null;
  try {
    santos = await listarSantos();
    log.info(`página inicial: ${santos.length} santos carregados (${santos.filter(s => s.ativo).length} ativos)`);
  } catch (e) {
    erro = "O servidor de conversa está indisponível no momento. Tente recarregar em instantes.";
    log.error(`página inicial: não consegui listar os santos: ${(e as Error).message}`);
  }
  return (
    <main>
      <h1>Santos Vivos</h1>
      <p className="sub">Escolha um santo e converse com ele a partir dos seus próprios escritos.</p>
      <p className="aviso">Simulações baseadas em fontes reais. Não substituem a Igreja, um padre ou um diretor espiritual. Prova de conceito.</p>
      {erro && <p className="erro">{erro}</p>}
      <div className="grid">{santos.map(s => <Card key={s.slug} s={s} />)}</div>
    </main>
  );
}
