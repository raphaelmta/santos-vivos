package br.santosvivos.santos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.file.Path;

public record Santo(String slug, String nome, String nomeCurto, String subtitulo, String descricao, boolean ativo,
                    String foto, String fotoCredito, String idiomaCorpus, String fidelidadeVoz,
                    @JsonIgnore Path dir) {
    public boolean temFoto() { return foto != null && dir.resolve(foto).toFile().isFile(); }
    public String fotoUrl() { return temFoto() ? "/api/santos/" + slug + "/foto" : null; }
}
