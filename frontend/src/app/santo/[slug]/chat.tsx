"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ApiError, conversar, type Msg, type Resposta, type Santo } from "@/lib/api";

type Item = { role: "user" | "assistant"; content: string; detalhe?: Resposta; erro?: string; digitando?: boolean; pergunta?: string };

export default function Chat({ santo }: { santo: Santo }) {
  const [itens, setItens] = useState<Item[]>([]);
  const [texto, setTexto] = useState("");
  const [ocupado, setOcupado] = useState(false);
  const fim = useRef<HTMLDivElement>(null);
  useEffect(() => { fim.current?.scrollIntoView({ behavior: "smooth" }); }, [itens]);

  async function perguntar(pergunta: string) {
    if (!pergunta || ocupado) return;
    setOcupado(true);
    const historico: Msg[] = itens.filter(i => !i.erro && !i.digitando).map(i => ({ role: i.role, content: i.content })).slice(-12);
    setItens(prev => [...prev, { role: "user", content: pergunta }, { role: "assistant", content: "", digitando: true }]);
    try {
      const r = await conversar(santo.slug, historico, pergunta);
      // exibição só depois da verificação; o efeito de digitação é apenas visual
      let i = 0;
      await new Promise<void>(resolve => {
        const t = setInterval(() => {
          i += 3;
          const fimTexto = i >= r.resposta.length;
          setItens(prev => { const c = [...prev]; c[c.length - 1] = { role: "assistant", content: r.resposta.slice(0, i), digitando: !fimTexto, detalhe: fimTexto ? r : undefined }; return c; });
          if (fimTexto) { clearInterval(t); resolve(); }
        }, 12);
      });
    } catch (e) {
      const err = e instanceof ApiError ? e : new ApiError("Algo deu errado ao enviar sua pergunta. Tente de novo.", String(e), 0);
      console.error(`[chat] falha ao perguntar a ${santo.slug}: ${err.detalhe}`);
      setItens(prev => { const c = [...prev]; c[c.length - 1] = { role: "assistant", content: "", erro: err.amigavel, pergunta }; return c; });
    } finally { setOcupado(false); }
  }

  function tentarDeNovo(idx: number) {
    const pergunta = itens[idx].pergunta ?? "";
    setItens(prev => prev.slice(0, idx - 1)); // remove a pergunta e o erro antes de reenviar
    setTimeout(() => perguntar(pergunta), 0);
  }

  return (
    <main>
      <div className="cab">
        {santo.fotoUrl ? <img src={santo.fotoUrl} alt={santo.nome} /> : <div className="semfoto">✝</div>}
        <div>
          <Link className="voltar" href="/">← todos os santos</Link>
          <h1 style={{ margin: 0 }}>{santo.nome}</h1>
          <div className="sub" style={{ margin: 0 }}>{santo.subtitulo}</div>
          {santo.fidelidadeVoz && <div className="fid">Fidelidade de voz: {santo.fidelidadeVoz}</div>}
        </div>
      </div>
      <p className="aviso">Esta é uma simulação baseada nos escritos de {santo.nomeCurto}. Não é {santo.nomeCurto}, nem substitui a Igreja, um padre ou um diretor espiritual.</p>

      {itens.map((it, idx) => (
        <div key={idx} className={`msg ${it.role}`}>
          <div className="who">{it.role === "user" ? "Você" : <>{santo.fotoUrl && <img src={santo.fotoUrl} alt="" />}{santo.nomeCurto}</>}</div>
          {it.erro
            ? <span className="erro">{it.erro} <button type="button" className="link" onClick={() => tentarDeNovo(idx)} disabled={ocupado}>Tentar de novo</button></span>
            : it.digitando && !it.content ? <span className="typing">{santo.nomeCurto} está escrevendo…</span>
            : it.content}
          {it.detalhe && (
            <>
              {it.detalhe.fontes.length > 0 && (
                <div className="fontes"><b>Fontes</b>
                  {it.detalhe.fontes.map(f => (
                    <details key={f.n}>
                      <summary>[{f.n}] {f.secao} — {f.titulo}{f.tipo === "secundaria" ? " (fonte secundária)" : ""}</summary>
                      <blockquote>{f.texto}</blockquote>
                      {f.original && <details><summary>original em francês</summary><blockquote lang="fr">{f.original}</blockquote></details>}
                      <a href={f.url} target="_blank" rel="noopener noreferrer">ver a fonte ↗</a>{f.licenca && <span className="meta"> · {f.licenca}</span>}
                    </details>
                  ))}
                </div>
              )}
              <div className="meta">{it.detalhe.categoria} · verificador: {it.detalhe.veredito}{it.detalhe.tentativas > 1 ? " (reescrita)" : ""}{it.detalhe.cache ? " · resposta em cache" : ` · ${it.detalhe.segundos}s`}</div>
            </>
          )}
        </div>
      ))}
      <div ref={fim} />
      {santo.fotoCredito && <p className="credito">Foto: {santo.fotoCredito}</p>}

      <form className="envio" onSubmit={e => { e.preventDefault(); const p = texto.trim(); setTexto(""); perguntar(p); }}>
        <div>
          <input value={texto} onChange={e => setTexto(e.target.value)} placeholder={`Pergunte algo a ${santo.nomeCurto}...`} autoFocus />
          <button disabled={ocupado}>Enviar</button>
        </div>
      </form>
    </main>
  );
}
