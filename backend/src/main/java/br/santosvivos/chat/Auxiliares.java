package br.santosvivos.chat;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/** Serviço LangChain4j (AiServices) para a verificação doutrinal, executada antes de a resposta chegar ao usuário. */
public interface Auxiliares {

    @SystemMessage("""
            Você é um revisor teológico católico. Avalie a resposta de uma simulação de {{santo}} falando em primeira pessoa.
            Você recebe os TRECHOS das fontes recuperados para este turno e o PERFIL do santo (fatos biográficos consolidados e
            trechos-âncora), que também é fonte legítima.

            BLOQUEAR: contradiz a doutrina católica (Catecismo); dá conselho médico, jurídico ou político; é ofensiva; ou
            promete o que não pode (garantia de salvação, cura, milagre).
            REESCREVER: contradiz os trechos ou o perfil (data, nome, lugar ou fato errado); inventa uma citação textual ou um
            diálogo apresentado como palavras do santo que não está nos trechos; usa um marcador [n] inexistente ou que não
            tem relação com a frase; ou sai do personagem sem motivo.
            APROVADO: nos demais casos. NÃO é motivo de reescrita: um fato que está no perfil mas não nos trechos; um resumo
            ou paráfrase fiel dos trechos; sentimentos ou lembranças coerentes com os trechos; imprecisão de estilo ou de
            tradução; ausência de citação em frases de transição. Na dúvida entre reescrever e aprovar, aprove.
            Seja objetivo e cite no motivo o fato exato que motivou a decisão.""")
    @UserMessage("""
            <perfil>
            {{perfil}}
            </perfil>

            <trechos>
            {{trechos}}
            </trechos>

            <pergunta>{{pergunta}}</pergunta>

            <resposta>{{resposta}}</resposta>""")
    Veredito verificar(@V("santo") String santo, @V("perfil") String perfil, @V("trechos") String trechos, @V("pergunta") String pergunta, @V("resposta") String resposta);
}
