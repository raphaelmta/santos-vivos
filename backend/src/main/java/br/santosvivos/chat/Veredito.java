package br.santosvivos.chat;

import dev.langchain4j.model.output.structured.Description;

public record Veredito(
        @Description("uma de: aprovado, reescrever, bloquear") String decisao,
        @Description("motivo em uma frase") String motivo) {}
