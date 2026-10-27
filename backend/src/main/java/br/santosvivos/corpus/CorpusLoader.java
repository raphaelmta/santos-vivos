package br.santosvivos.corpus;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lê os Markdown com frontmatter em <santo>/corpus/<idioma>/ e divide em trechos de ~1400 caracteres sem cortar parágrafos.
 * Arquivos traduzidos trazem marcadores "<!-- trecho N -->" que preservam a segmentação do original, permitindo
 * emparelhar cada trecho traduzido com o texto original em <santo>/corpus/<idioma_original>/.
 */
public class CorpusLoader {
    private static final Pattern FM = Pattern.compile("^---\\n(.*?)\\n---\\n\\n?(.*)$", Pattern.DOTALL);
    public static final String MARCADOR = "<!-- trecho %03d -->";
    private static final Pattern MARCADOR_RE = Pattern.compile("<!-- trecho (\\d{3}) -->\\n?");
    private static final ObjectMapper om = new ObjectMapper();
    private static final int ALVO = 1400, MAXIMO = 2200;

    public record Doc(Map<String, String> meta, String body) {}

    public static Doc lerDoc(Path p) throws IOException {
        Matcher m = FM.matcher(Files.readString(p));
        if (!m.matches()) return null;
        Map<String, String> meta = new LinkedHashMap<>();
        for (String line : m.group(1).split("\n")) {
            int i = line.indexOf(": ");
            if (i > 0) { var v = om.readTree(line.substring(i + 2)); meta.put(line.substring(0, i), v.isNull() ? null : v.asText()); }
        }
        return new Doc(meta, m.group(2));
    }

    public static List<Doc> lerDocs(Path dir) throws IOException {
        List<Doc> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".md")).sorted().toList()) {
                Doc d = lerDoc(p); if (d != null) out.add(d);
            }
        }
        return out;
    }

    /** Segmenta o corpo de um documento (sem marcadores) em trechos. */
    public static List<String> segmentar(Doc d) {
        return agrupar(paragrafos(d.body(), "poesias".equals(d.meta().get("secao"))));
    }

    /** Se o corpo tem marcadores de trecho, devolve os trechos na ordem; senão, segmenta. */
    public static List<String> trechosDe(Doc d) {
        if (!d.body().contains("<!-- trecho ")) return segmentar(d);
        List<String> out = new ArrayList<>();
        String[] parts = MARCADOR_RE.split(d.body());
        for (String s : parts) { s = s.strip(); if (!s.isEmpty()) out.add(s); }
        return out;
    }

    /** Carrega o corpus do idioma do índice, emparelhando com os originais quando existirem. */
    public static List<Trecho> carregar(Path corpusRoot, String idioma) throws IOException {
        List<Trecho> out = new ArrayList<>();
        Map<String, Doc> originais = new HashMap<>();
        for (Doc d : lerDocs(corpusRoot.resolve(idioma))) {
            String id = d.meta().get("id");
            String idiomaOrig = d.meta().getOrDefault("idioma_original", idioma);
            List<String> orig = null;
            if (!idioma.equals(idiomaOrig)) {
                Path po = corpusRoot.resolve(idiomaOrig).resolve(id + ".md");
                if (Files.isRegularFile(po)) orig = segmentar(lerDoc(po));
            }
            List<String> trechos = trechosDe(d);
            for (int i = 0; i < trechos.size(); i++) {
                out.add(new Trecho(id + "#" + String.format("%03d", i), id, i, d.meta().get("secao"),
                        d.meta().getOrDefault("tipo", "primaria"), d.meta().getOrDefault("titulo_pt", d.meta().get("titulo")),
                        d.meta().get("obra"), d.meta().get("fonte_url"), trechos.get(i),
                        orig != null && i < orig.size() ? orig.get(i) : null, d.meta().get("licenca")));
            }
        }
        return out;
    }

    static List<String> paragrafos(String body, boolean poema) {
        body = body.replace("﻿", "").replaceAll("\\n\\d{2,3}%\\n", "\n");
        List<String> paras = new ArrayList<>();
        for (String b : body.split("\\n\\s*\\n")) {
            b = b.strip();
            if (b.isEmpty()) continue;
            if (poema) paras.add(b);
            else paras.add(b.replaceAll("^([A-ZÀ-Ý])\\n([a-zà-ÿ])", "$1$2").replaceAll("\\s*\\n\\s*", " "));
        }
        return paras;
    }

    static List<String> agrupar(List<String> paras) {
        List<String> chunks = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        int size = 0;
        for (String p : paras) {
            if (size + p.length() > MAXIMO && !cur.isEmpty()) { chunks.add(String.join("\n\n", cur)); cur.clear(); size = 0; }
            cur.add(p); size += p.length() + 2;
            if (size >= ALVO) { chunks.add(String.join("\n\n", cur)); cur.clear(); size = 0; }
        }
        if (!cur.isEmpty()) chunks.add(String.join("\n\n", cur));
        return chunks;
    }
}
