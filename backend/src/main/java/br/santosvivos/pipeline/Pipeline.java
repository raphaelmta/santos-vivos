package br.santosvivos.pipeline;

import br.santosvivos.corpus.CorpusLoader;
import br.santosvivos.corpus.CorpusLoader.Doc;
import br.santosvivos.santos.Santo;
import br.santosvivos.santos.SantoRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Tarefas de preparação do corpus, executadas com:
 *   mvn -q spring-boot:run -Dspring-boot.run.arguments="--santos.tarefa=<tarefa> --santos.alvo=teresinha --spring.main.web-application-type=none"
 * Tarefas: baixar (Wikisource FR -> corpus/fr), traduzir (corpus/fr -> corpus/pt via LLM), biografia (Wikipédia PT -> corpus/pt), tudo.
 */
@Component
public class Pipeline implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(Pipeline.class);
    private static final ObjectMapper om = new ObjectMapper();
    private static final String UA = "santos-vivos/0.1 (projeto pessoal sem fins lucrativos)";
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private final SantoRegistry registry;
    private final ChatModel model;
    private final String tarefa, alvo;
    private final int paralelismo;

    public Pipeline(SantoRegistry registry, ChatModel model, @Value("${santos.tarefa:}") String tarefa,
                    @Value("${santos.alvo:teresinha}") String alvo, @Value("${santos.pipeline.paralelismo:6}") int paralelismo) {
        this.registry = registry; this.model = model; this.tarefa = tarefa; this.alvo = alvo; this.paralelismo = paralelismo;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (tarefa.isBlank()) return;
        Santo s = registry.por(alvo).orElseThrow(() -> new IllegalArgumentException("santo desconhecido: " + alvo));
        switch (tarefa) {
            case "baixar" -> baixarWikisource(s);
            case "traduzir" -> traduzir(s);
            case "biografia" -> biografiaWikipedia(s);
            case "tudo" -> { baixarWikisource(s); biografiaWikipedia(s); traduzir(s); }
            default -> throw new IllegalArgumentException("tarefa desconhecida: " + tarefa);
        }
        log.info("tarefa '{}' concluída", tarefa);
        System.exit(0);
    }

    // ---------- 1. Wikisource FR: Histoire d'une âme (ed. 1912), domínio público ----------
    private static final String WS_API = "https://fr.wikisource.org/w/api.php";
    private static final String WS_ROOT = "Histoire d’une âme";
    private static final Map<String, String> SECOES = Map.of("1", "autobiografia", "2", "conselhos_e_oracoes", "3", "cartas", "4", "poesias");

    void baixarWikisource(Santo s) throws Exception {
        Path out = s.dir().resolve("corpus/fr"); Files.createDirectories(out);
        JsonNode links = get(WS_API + "?action=parse&format=json&prop=links&page=" + enc(WS_ROOT)).path("parse").path("links");
        List<String> pages = new ArrayList<>();
        for (JsonNode l : links) if (l.path("ns").asInt() == 0 && l.path("*").asText().startsWith(WS_ROOT + "/")) pages.add(l.path("*").asText());
        pages.sort(Comparator.comparing(p -> Arrays.stream(p.substring(WS_ROOT.length() + 1).split("/")).map(x -> String.format("%05d", Integer.parseInt(x))).toList().toString()));
        int n = 0;
        for (String p : pages) {
            String sub = p.substring(WS_ROOT.length() + 1);
            String top = sub.split("/")[0];
            if (!SECOES.containsKey(top) || !sub.contains("/")) continue; // partes 5-6 (milagres/exumação) e índices ficam de fora
            String slug = "hda-" + sub.replace('/', '-');
            Path f = out.resolve(slug + ".md");
            if (Files.exists(f)) { n++; continue; }
            JsonNode parse = get(WS_API + "?action=parse&format=json&disabletoc=1&prop=text&page=" + enc(p)).path("parse");
            Document html = Jsoup.parse(parse.path("text").path("*").asText());
            html.select("style, script, .ws-noexport, .headertemplate, .references, sup.reference, .mw-editsection, table, .noprint, .mw-references-wrap").remove();
            String titulo = null;
            for (var h : html.select(".ws-title, h2, h3, .titre, .t2, .t3")) { if (!h.text().isBlank()) { titulo = h.text(); break; } }
            html.outputSettings().prettyPrint(false);
            String text = Jsoup.parse(html.html().replaceAll("(?i)<br[^>]*>", "\n").replaceAll("(?i)</(p|div|h\\d|li)>", "\n\n")).wholeText()
                    .replace("﻿", "").replaceAll("[ \\t\\u00a0]+", " ").replaceAll(" ?\\n ?", "\n").replaceAll("\\n{3,}", "\n\n").strip();
            if (text.length() < 200) continue;
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("id", slug); fm.put("santo", s.slug()); fm.put("obra", "Histoire d'une âme (ed. 1912)"); fm.put("secao", SECOES.get(top));
            fm.put("tipo", "primaria"); fm.put("titulo", titulo == null ? sub : titulo); fm.put("idioma_original", "fr");
            fm.put("fonte_url", "https://fr.wikisource.org/wiki/" + p.replace(' ', '_')); fm.put("licenca", "dominio_publico"); fm.put("status_traducao", "original");
            Files.writeString(f, frontmatter(fm) + text + "\n");
            n++; log.info("baixado {} ({} chars) {}", slug, text.length(), titulo);
            Thread.sleep(1500);
        }
        log.info("{} arquivos em {}", n, out);
    }

    // ---------- 2. Tradução FR -> PT por trecho, preservando a segmentação ----------
    private static final String SYS_TRAD = """
            Você é um tradutor literário do francês para o português do Brasil, especializado em textos espirituais católicos do
            século XIX. Traduza o trecho fielmente, preservando o tom íntimo, as imagens, as citações bíblicas (grafia católica
            brasileira dos nomes e livros), os diminutivos, as reticências e exclamações características de Santa Teresinha.
            Em poesia, preserve os versos linha a linha, sem forçar rima. Não resuma, não explique, não acrescente notas,
            não use markdown. Devolva apenas a tradução.""";

    void traduzir(Santo s) throws Exception {
        Path fr = s.dir().resolve("corpus/fr"), pt = s.dir().resolve("corpus/pt");
        Files.createDirectories(pt);
        List<Doc> docs = CorpusLoader.lerDocs(fr);
        ExecutorService ex = Executors.newFixedThreadPool(paralelismo);
        int feitos = 0, total = 0;
        for (Doc d : docs) {
            String id = d.meta().get("id");
            Path f = pt.resolve(id + ".md");
            List<String> trechos = CorpusLoader.segmentar(d);
            total += trechos.size();
            if (Files.exists(f)) { feitos += trechos.size(); continue; }
            long t0 = System.currentTimeMillis();
            List<Future<String>> futs = new ArrayList<>();
            for (String t : trechos) futs.add(ex.submit(() -> traduzirTexto(t)));
            Future<String> tituloFut = ex.submit(() -> traduzirTexto(d.meta().get("titulo")));
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < futs.size(); i++) body.append(String.format(CorpusLoader.MARCADOR, i)).append("\n").append(futs.get(i).get()).append("\n\n");
            Map<String, Object> fm = new LinkedHashMap<>(d.meta());
            fm.put("titulo_pt", tituloFut.get().replaceAll("\\s+", " ").strip());
            fm.put("status_traducao", "traduzido_ia"); fm.put("traducao", "Claude (Haiku) via pipeline; não revisada por humano");
            Files.writeString(f, frontmatter(fm) + body.toString().strip() + "\n");
            feitos += trechos.size();
            log.info("traduzido {} ({} trechos, {} s) — {}/{} trechos no total", id, trechos.size(), (System.currentTimeMillis() - t0) / 1000, feitos, total);
        }
        ex.shutdown();
    }

    private String traduzirTexto(String texto) {
        for (int tentativa = 1; ; tentativa++) {
            try {
                return model.chat(SystemMessage.from(SYS_TRAD), UserMessage.from(texto)).aiMessage().text().strip();
            } catch (Exception e) {
                if (tentativa >= 3) throw e;
                log.warn("tradução falhou (tentativa {}): {}", tentativa, e.getMessage());
                try { Thread.sleep(3000L * tentativa); } catch (InterruptedException ie) { throw new RuntimeException(ie); }
            }
        }
    }

    // ---------- 3. Biografia: Wikipédia em português (CC BY-SA 4.0), fonte secundária ----------
    void biografiaWikipedia(Santo s) throws Exception {
        Path pt = s.dir().resolve("corpus/pt"); Files.createDirectories(pt);
        String titulo = "Teresa de Lisieux";
        JsonNode q = get("https://pt.wikipedia.org/w/api.php?action=query&format=json&prop=extracts%7Cinfo&inprop=url&explaintext=1&redirects=1&titles=" + enc(titulo));
        JsonNode page = q.path("query").path("pages").elements().next();
        String texto = page.path("extract").asText();
        // remove seções sem valor biográfico
        texto = texto.replaceAll("(?s)\\n== (Ver também|Referências|Bibliografia|Ligações externas|Notas)[^\\n]*==.*$", "");
        texto = texto.replaceAll("\\n=+ ([^=]+?) =+\\n", "\n\n$1\n\n").replaceAll("\\n{3,}", "\n\n").strip();
        Map<String, Object> fm = new LinkedHashMap<>();
        fm.put("id", "wiki-biografia"); fm.put("santo", s.slug()); fm.put("obra", "Wikipédia em português: " + page.path("title").asText());
        fm.put("secao", "biografia"); fm.put("tipo", "secundaria"); fm.put("titulo", "Biografia (Wikipédia)"); fm.put("titulo_pt", "Biografia (Wikipédia)");
        fm.put("idioma_original", "pt"); fm.put("fonte_url", page.path("fullurl").asText()); fm.put("licenca", "CC BY-SA 4.0 (atribuição: Wikipédia, colaboradores)");
        fm.put("status_traducao", "original");
        Files.writeString(pt.resolve("wiki-biografia.md"), frontmatter(fm) + texto + "\n");
        log.info("biografia da Wikipédia gravada ({} chars)", texto.length());
    }

    // ---------- util ----------
    private JsonNode get(String url) throws Exception {
        for (int tentativa = 0; tentativa < 6; tentativa++) {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 429) { Thread.sleep(5000L * (tentativa + 1)); continue; }
            if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " em " + url);
            return om.readTree(r.body());
        }
        throw new IOException("429 persistente em " + url);
    }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    static String frontmatter(Map<String, Object> fm) throws IOException {
        StringBuilder sb = new StringBuilder("---\n");
        for (var e : fm.entrySet()) sb.append(e.getKey()).append(": ").append(om.writeValueAsString(e.getValue())).append("\n");
        return sb.append("---\n\n").toString();
    }
}
