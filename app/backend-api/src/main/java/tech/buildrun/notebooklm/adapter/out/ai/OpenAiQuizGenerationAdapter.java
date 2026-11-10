package tech.buildrun.notebooklm.adapter.out.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.core.domain.chunk.ChunkText;
import tech.buildrun.notebooklm.core.domain.quiz.QuizQuestion;
import tech.buildrun.notebooklm.core.port.out.ai.StructuredGenerationPort;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OpenAiQuizGenerationAdapter implements StructuredGenerationPort {

    private static final String PROMPT = """
            Você gera quizzes de estudo. Com base SOMENTE nos trechos abaixo, crie exatamente %d perguntas de múltipla escolha.
            Cada pergunta tem exatamente %d opções, apenas 1 correta (correctIndex de 0 a %d) e uma explanation que justifica
            a resposta correta com base nos trechos. Distribua as perguntas entre os tópicos. Escreva no idioma dos trechos.

            Tópicos: %s

            Trechos:
            %s
            """;

    private final ChatClient chatClient;

    public OpenAiQuizGenerationAdapter(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public List<QuizQuestion> generateQuiz(List<String> topics, List<ChunkText> chunks, int questionCount,
                                           int optionCount) {
        String prompt = PROMPT.formatted(questionCount, optionCount, optionCount - 1, String.join(", ", topics),
                chunks.stream().map(ChunkText::text).collect(Collectors.joining("\n---\n")));
        QuizOutput output = callModel(prompt);
        if (output == null || output.questions() == null) {
            return null;
        }
        return output.questions().stream()
                .map(q -> new QuizQuestion(q.question(), q.options(), q.correctIndex(), q.explanation())).toList();
    }

    QuizOutput callModel(String prompt) {
        String schema = new BeanOutputConverter<>(QuizOutput.class).getJsonSchema();

        var options = OpenAiChatOptions.builder()
                .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_SCHEMA).jsonSchema(schema).build())
                .extraBody(Map.of("provider", Map.of("require_parameters", true)));

        return chatClient.prompt().user(prompt).options(options).call().entity(QuizOutput.class);
    }
}
