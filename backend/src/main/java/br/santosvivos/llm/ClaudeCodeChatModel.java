package br.santosvivos.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ChatModel do LangChain4j que usa o Claude Code local (`claude -p`) como modelo.
 * Serve apenas para desenvolvimento local: usa a sessão logada do Claude Code, sem chave de API.
 * Em produção, troque por AnthropicChatModel via `santos.llm=anthropic`.
 */
public class ClaudeCodeChatModel implements ChatModel {
    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeChatModel.class);
    private static final ObjectMapper json = new ObjectMapper();

    private final String model;
    private final long timeoutSeconds;

    public ClaudeCodeChatModel(String model, long timeoutSeconds) {
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        StringBuilder system = new StringBuilder();
        StringBuilder prompt = new StringBuilder();
        List<ChatMessage> msgs = request.messages();
        // Histórico vira transcrição; a última mensagem do usuário é a pergunta atual.
        List<ChatMessage> history = new ArrayList<>();
        ChatMessage last = msgs.get(msgs.size() - 1);
        for (ChatMessage m : msgs) {
            if (m instanceof SystemMessage s) system.append(s.text()).append("\n\n");
            else if (m != last) history.add(m);
        }
        if (!history.isEmpty()) {
            prompt.append("<historico_da_conversa>\n");
            for (ChatMessage m : history) {
                if (m instanceof UserMessage u) prompt.append("Usuário: ").append(u.singleText()).append("\n\n");
                else if (m instanceof AiMessage a) prompt.append("Você: ").append(a.text()).append("\n\n");
            }
            prompt.append("</historico_da_conversa>\n\n");
        }
        prompt.append(last instanceof UserMessage u ? u.singleText() : last.toString());

        List<String> cmd = new ArrayList<>(List.of("claude", "-p", "--model", model, "--output-format", "json",
                "--no-session-persistence", "--tools", ""));
        if (!system.isEmpty()) { cmd.add("--system-prompt"); cmd.add(system.toString().trim()); }

        long t0 = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(false);
            pb.environment().remove("CLAUDECODE"); // permite rodar mesmo de dentro de outra sessão
            Process p = pb.start();
            try (OutputStream os = p.getOutputStream()) { os.write(prompt.toString().getBytes(StandardCharsets.UTF_8)); }
            // drena stdout/stderr em threads para que o timeout valha mesmo se o processo travar com os pipes abertos
            CompletableFuture<String> outF = CompletableFuture.supplyAsync(() -> ler(p.getInputStream()));
            CompletableFuture<String> errF = CompletableFuture.supplyAsync(() -> ler(p.getErrorStream()));
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new RuntimeException("claude -p: timeout após " + timeoutSeconds + "s");
            }
            String out = outF.get(10, TimeUnit.SECONDS), err = errF.get(10, TimeUnit.SECONDS);
            if (p.exitValue() != 0) throw new RuntimeException("claude -p falhou (" + p.exitValue() + "): " + err.trim());
            JsonNode node = json.readTree(out);
            if (node.path("is_error").asBoolean(false)) throw new RuntimeException("claude -p erro: " + node.path("result").asText());
            String text = node.path("result").asText("");
            JsonNode usage = node.path("usage");
            int in = usage.path("input_tokens").asInt() + usage.path("cache_read_input_tokens").asInt() + usage.path("cache_creation_input_tokens").asInt();
            int outTok = usage.path("output_tokens").asInt();
            log.info("claude -p ({}): {} ms, in={} out={} custo=US${}", model, System.currentTimeMillis() - t0, in, outTok, node.path("total_cost_usd").asText());
            return ChatResponse.builder().aiMessage(AiMessage.from(text)).modelName(model)
                    .tokenUsage(new TokenUsage(in, outTok)).build();
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException("Falha ao executar claude -p: " + e.getMessage(), e);
        }
    }

    private static String ler(java.io.InputStream in) {
        try { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        catch (IOException e) { return ""; }
    }
}
