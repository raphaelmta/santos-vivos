package br.santosvivos.santos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/** Lê <root>/<slug>/santo.json. Cada santo é um "pacote": metadados, foto, voz.md, corpus/. */
@Component
public class SantoRegistry {
    private static final Logger log = LoggerFactory.getLogger(SantoRegistry.class);
    private final Map<String, Santo> santos = new LinkedHashMap<>();
    private final Path root;

    public SantoRegistry(@Value("${santos.root}") String rootDir) throws IOException {
        this.root = Path.of(rootDir).toAbsolutePath().normalize();
        ObjectMapper om = new ObjectMapper();
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path d : dirs.sorted().toList()) {
                Path meta = d.resolve("santo.json");
                if (!Files.isRegularFile(meta)) continue;
                JsonNode n = om.readTree(meta.toFile());
                Santo s = new Santo(n.path("slug").asText(d.getFileName().toString()), n.path("nome").asText(),
                        n.path("nomeCurto").asText(), n.path("subtitulo").asText(), n.path("descricao").asText(),
                        n.path("ativo").asBoolean(false), n.path("foto").isNull() ? null : n.path("foto").asText(null),
                        n.path("fotoCredito").asText(null), n.path("idiomaCorpus").asText(null),
                        n.path("fidelidadeVoz").asText(null), d);
                santos.put(s.slug(), s);
            }
        }
        // ativos primeiro
        List<Santo> ordered = santos.values().stream().sorted(Comparator.comparing((Santo s) -> !s.ativo())).toList();
        santos.clear(); ordered.forEach(s -> santos.put(s.slug(), s));
        log.info("{} santos carregados de {}: {}", santos.size(), root, santos.keySet());
    }

    public Collection<Santo> todos() { return santos.values(); }
    public Optional<Santo> por(String slug) { return Optional.ofNullable(santos.get(slug)); }
    public Path root() { return root; }
}
