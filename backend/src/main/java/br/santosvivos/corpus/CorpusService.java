package br.santosvivos.corpus;

import br.santosvivos.santos.Santo;
import br.santosvivos.santos.SantoRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Carrega, para cada santo ativo, o corpus no idioma do índice (santo.json: idiomaCorpus) e o perfil de voz (voz.md). */
@Service
public class CorpusService {
    private static final Logger log = LoggerFactory.getLogger(CorpusService.class);
    private final Map<String, IndiceLucene> indices = new HashMap<>();
    private final Map<String, String> vozes = new HashMap<>();

    public CorpusService(SantoRegistry registry) throws IOException {
        for (Santo s : registry.todos()) {
            if (!s.ativo()) continue;
            String idioma = s.idiomaCorpus() == null ? "pt" : s.idiomaCorpus();
            Path corpus = s.dir().resolve("corpus");
            List<Trecho> trechos = CorpusLoader.carregar(corpus, idioma);
            if (trechos.isEmpty() && !"fr".equals(idioma) && Files.isDirectory(corpus.resolve("fr"))) {
                log.warn("{}: corpus/{} vazio; usando corpus/fr (rode a tarefa 'traduzir')", s.slug(), idioma);
                idioma = "fr"; trechos = CorpusLoader.carregar(corpus, "fr");
            }
            indices.put(s.slug(), new IndiceLucene(trechos, idioma));
            Path voz = s.dir().resolve("voz.md");
            vozes.put(s.slug(), Files.isRegularFile(voz) ? Files.readString(voz) : "(perfil de voz ainda não gerado)");
            long comOriginal = trechos.stream().filter(t -> t.original() != null).count();
            log.info("{}: {} trechos indexados ({}; {} com original emparelhado), voz.md {}", s.slug(), trechos.size(), idioma, comOriginal, Files.isRegularFile(voz) ? "ok" : "AUSENTE");
        }
    }

    public IndiceLucene indice(String slug) { return indices.get(slug); }
    public String voz(String slug) { return vozes.getOrDefault(slug, ""); }

    /** Seções factuais do perfil (fatos consolidados e trechos-âncora), usadas pelo verificador como fonte legítima. */
    public String fatosDoPerfil(String slug) {
        String voz = voz(slug);
        StringBuilder sb = new StringBuilder();
        for (String titulo : List.of("## Quem eu sou", "## Fatos biográficos consolidados", "## Dez trechos-âncora")) {
            int i = voz.indexOf(titulo);
            if (i < 0) continue;
            int j = voz.indexOf("\n## ", i + titulo.length());
            sb.append(voz, i, j < 0 ? voz.length() : j).append("\n\n");
        }
        return sb.isEmpty() ? "(perfil sem seções factuais)" : sb.toString().strip();
    }
}
