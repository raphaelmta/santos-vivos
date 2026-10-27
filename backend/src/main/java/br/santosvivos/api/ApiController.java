package br.santosvivos.api;

import br.santosvivos.chat.ChatService;
import br.santosvivos.santos.Santo;
import br.santosvivos.santos.SantoRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class ApiController {
    private static final Logger log = LoggerFactory.getLogger(ApiController.class);
    private final SantoRegistry registry;
    private final ChatService chat;

    public ApiController(SantoRegistry registry, ChatService chat) { this.registry = registry; this.chat = chat; }

    public record SantoDto(String slug, String nome, String nomeCurto, String subtitulo, String descricao, boolean ativo,
                           String fotoUrl, String fotoCredito, String fidelidadeVoz) {
        static SantoDto de(Santo s) { return new SantoDto(s.slug(), s.nome(), s.nomeCurto(), s.subtitulo(), s.descricao(), s.ativo(), s.fotoUrl(), s.fotoCredito(), s.fidelidadeVoz()); }
    }
    public record ChatReq(List<ChatService.Msg> historico, String pergunta) {}

    @GetMapping("/santos")
    public List<SantoDto> santos() { return registry.todos().stream().map(SantoDto::de).toList(); }

    @GetMapping("/santos/{slug}")
    public SantoDto santo(@PathVariable String slug) { return SantoDto.de(buscar(slug)); }

    @GetMapping("/santos/{slug}/foto")
    public ResponseEntity<byte[]> foto(@PathVariable String slug) throws IOException {
        Santo s = buscar(slug);
        if (!s.temFoto()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).header("Cache-Control", "public, max-age=86400")
                .body(Files.readAllBytes(s.dir().resolve(s.foto())));
    }

    @PostMapping("/santos/{slug}/chat")
    public ChatService.Resposta chat(@PathVariable String slug, @RequestBody ChatReq req) throws IOException {
        Santo s = buscar(slug);
        if (!s.ativo()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Santo em preparação");
        if (req.pergunta() == null || req.pergunta().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pergunta vazia");
        List<ChatService.Msg> hist = req.historico() == null ? List.of() : req.historico();
        hist = hist.subList(Math.max(0, hist.size() - 12), hist.size());
        return chat.responder(s, hist, req.pergunta().strip());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> erro(Exception e) {
        if (e instanceof ResponseStatusException rse) {
            log.warn("requisição recusada: {} {}", rse.getStatusCode().value(), rse.getReason());
            return ResponseEntity.status(rse.getStatusCode()).body(Map.of("erro", String.valueOf(rse.getReason()), "status", rse.getStatusCode().value()));
        }
        log.error("erro não tratado ao atender requisição", e);
        return ResponseEntity.status(500).body(Map.of("erro", "Não consegui gerar a resposta agora. Tente de novo em instantes.",
                "detalhe", e.getMessage() == null ? e.toString() : e.getMessage(), "status", 500));
    }

    private Santo buscar(String slug) { return registry.por(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "santo não encontrado")); }
}
