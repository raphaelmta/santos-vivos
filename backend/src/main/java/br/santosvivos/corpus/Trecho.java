package br.santosvivos.corpus;

/** Um trecho indexável. `texto` é o que o modelo lê (no idioma do índice); `original` é o texto na língua original, se diferente. */
public record Trecho(String id, String docId, int idx, String secao, String tipo, String titulo, String obra, String url,
                     String texto, String original, String licenca) {}
