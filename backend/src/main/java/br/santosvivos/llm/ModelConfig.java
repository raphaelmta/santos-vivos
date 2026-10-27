package br.santosvivos.llm;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelConfig {
    private static final Logger log = LoggerFactory.getLogger(ModelConfig.class);

    @Bean
    public ChatModel chatModel(@Value("${santos.llm}") String provider,
                               @Value("${santos.claude-code.model}") String ccModel,
                               @Value("${santos.claude-code.timeout-seconds}") long ccTimeout,
                               @Value("${santos.anthropic.model}") String anthropicModel) {
        if ("anthropic".equalsIgnoreCase(provider)) {
            String key = System.getenv("ANTHROPIC_API_KEY");
            if (key == null || key.isBlank()) throw new IllegalStateException("santos.llm=anthropic exige ANTHROPIC_API_KEY");
            log.info("LLM: API Anthropic, modelo {}", anthropicModel);
            return AnthropicChatModel.builder().apiKey(key).modelName(anthropicModel)
                    .maxTokens(1500).cacheSystemMessages(true).build();
        }
        log.info("LLM: Claude Code local (claude -p --model {})", ccModel);
        return new ClaudeCodeChatModel(ccModel, ccTimeout);
    }
}
