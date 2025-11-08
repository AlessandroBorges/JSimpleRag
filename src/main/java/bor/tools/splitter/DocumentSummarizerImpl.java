package bor.tools.splitter;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import bor.tools.simplellm.CompletionResponse;
import bor.tools.simplellm.LLMProvider;
import bor.tools.simplellm.MapParam;
import bor.tools.simplellm.exceptions.LLMException;

/**
 * Implementação concreta da interface DocumentSummarizer.
 *
 * Esta classe fornece funcionalidades de sumarização de documentos e geração
 * de pares pergunta-resposta utilizando serviços de LLM.
 */
@Service
public class DocumentSummarizerImpl implements DocumentSummarizer {

    private static final Logger logger = LoggerFactory.getLogger(DocumentSummarizerImpl.class);

    private final LLMProvider llmService;

    /**
     * Tamanho máximo padrão para resumos (em tokens)
     */
    private static final int DEFAULT_SUMMARY_LENGTH = 500;

    /**
     * Construtor com injeção de dependência
     */
    public DocumentSummarizerImpl(LLMProvider llmService) {
        this.llmService = llmService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String summarize(@NonNull String text, int maxLength) {
        return summarize(text,
            "Resuma o texto de forma concisa, mantendo as informações mais importantes e preservando o contexto principal",
            maxLength);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String summarize(@NonNull String text, String instructions, int maxLength) {
        if (llmService == null) {
            logger.error("LLM service not available for summarization");
            return createFallbackSummary(text, maxLength);
        }

        logger.debug("Summarizing text of {} characters with maxLength: {}", text.length(), maxLength);

        try {
            // Ajustar texto se muito longo para o contexto do LLM
            String contentToSummarize = text;
            if (text.length() > 8000) { // Limite conservador para contexto
                contentToSummarize = text.substring(0, 8000) + "...";
                logger.debug("Truncated text to 8000 characters for summarization");
            }

            MapParam params = new MapParam();
            params.put("max_tokens", Math.min(maxLength, 2000)); // Limite de segurança
            params.put("temperature", 0.3); // Mais determinístico para resumos

            CompletionResponse response = llmService.completion(instructions, contentToSummarize, params);
            String summary = response.getText().trim();

            logger.debug("Successfully generated summary of {} characters", summary.length());
            return summary;

        } catch (LLMException e) {
            logger.error("Failed to generate summary using LLM: {}", e.getMessage());
            return createFallbackSummary(text, maxLength);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<QuestionAnswer> generateQA(@NonNull String text, int numQuestions) {
        if (llmService == null) {
            logger.error("LLM service not available for Q&A generation");
            return createFallbackQA(text, numQuestions);
        }

        logger.debug("Generating {} Q&A pairs for text of {} characters", numQuestions, text.length());

        try {
            // Ajustar texto se muito longo
            String contentForQA = text;
            if (text.length() > 6000) {
                contentForQA = text.substring(0, 6000) + "...";
                logger.debug("Truncated text to 6000 characters for Q&A generation");
            }

            String prompt = String.format(
                "Com base no texto fornecido, gere exatamente %d pares de pergunta e resposta relevantes e informativos. " +
                "As perguntas devem abordar os pontos principais do texto. " +
                "Formato: Q: [pergunta]\\nA: [resposta]\\n\\n" +
                "Certifique-se de que as respostas sejam concisas mas informativas.",
                numQuestions);

            MapParam params = new MapParam();
            params.put("max_tokens", numQuestions * 150); // ~150 tokens por Q&A
            params.put("temperature", 0.4); // Ligeiramente criativo mas controlado

            CompletionResponse response = llmService.completion(prompt, contentForQA, params);
            String result = response.getText();

            List<QuestionAnswer> qaList = parseQAResponse(result);

            // Validar se obtivemos o número esperado de Q&A
            if (qaList.size() < numQuestions) {
                logger.warn("Generated {} Q&A pairs instead of requested {}", qaList.size(), numQuestions);
            }

            logger.debug("Successfully generated {} Q&A pairs", qaList.size());
            return qaList;

        } catch (LLMException e) {
            logger.error("Failed to generate Q&A using LLM: {}", e.getMessage());
            return createFallbackQA(text, numQuestions);
        }
    }

    /**
     * Cria um resumo usando método de fallback (sem LLM)
     */
    private String createFallbackSummary(String text, int maxLength) {
        logger.debug("Creating fallback summary for text of {} characters", text.length());

        // Estratégia simples: pegar as primeiras frases até o limite
        String[] sentences = text.split("[\\.!?]+");
        StringBuilder summary = new StringBuilder();
        int targetWords = maxLength / 4; // Estimar ~4 caracteres por palavra

        int wordCount = 0;
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;

            String[] words = sentence.split("\\s+");
            if (wordCount + words.length <= targetWords) {
                if (summary.length() > 0) {
                    summary.append(". ");
                }
                summary.append(sentence);
                wordCount += words.length;
            } else {
                break;
            }
        }

        if (summary.length() == 0) {
            // Se nenhuma frase coube, pegar o início do texto
            summary.append(text.substring(0, Math.min(text.length(), maxLength)));
        }

        String result = summary.toString().trim();
        if (!result.endsWith(".") && !result.endsWith("!") && !result.endsWith("?")) {
            result += "...";
        }

        logger.debug("Created fallback summary of {} characters", result.length());
        return result;
    }

    /**
     * Cria Q&A usando método de fallback (sem LLM)
     */
    private List<QuestionAnswer> createFallbackQA(String text, int numQuestions) {
        logger.debug("Creating fallback Q&A for text of {} characters", text.length());

        List<QuestionAnswer> qaList = new ArrayList<>();

        // Estratégia simples: criar perguntas genéricas baseadas no conteúdo
        String[] sentences = text.split("[\\.!?]+");
        List<String> meaningfulSentences = new ArrayList<>();

        // Filtrar sentenças significativas (mais de 20 caracteres)
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.length() > 20) {
                meaningfulSentences.add(sentence);
            }
        }

        // Criar Q&A baseado nas sentenças mais significativas
        int questionsCreated = 0;
        for (int i = 0; i < meaningfulSentences.size() && questionsCreated < numQuestions; i++) {
            String sentence = meaningfulSentences.get(i);

            // Gerar pergunta simples
            String question = generateSimpleQuestion(sentence);
            String answer = sentence.trim();

            if (question != null && !answer.isEmpty()) {
                qaList.add(new QuestionAnswer(question, answer));
                questionsCreated++;
            }
        }

        // Se não conseguimos gerar Q&A suficientes, adicionar perguntas genéricas
        if (qaList.size() < numQuestions) {
            String summary = createFallbackSummary(text, 200);
            qaList.add(new QuestionAnswer("Qual é o resumo do documento?", summary));
        }

        logger.debug("Created {} fallback Q&A pairs", qaList.size());
        return qaList;
    }

    /**
     * Gera uma pergunta simples baseada em uma sentença
     */
    private String generateSimpleQuestion(String sentence) {
        sentence = sentence.trim().toLowerCase();

        // Estratégias simples para gerar perguntas
        if (sentence.contains("é") || sentence.contains("são")) {
            return "O que " + sentence.substring(0, Math.min(sentence.length(), 50)) + "?";
        }

        if (sentence.contains("quando")) {
            return "Quando " + sentence.substring(sentence.indexOf("quando") + 6, Math.min(sentence.length(), 50)) + "?";
        }

        if (sentence.contains("como")) {
            return "Como " + sentence.substring(sentence.indexOf("como") + 4, Math.min(sentence.length(), 50)) + "?";
        }

        if (sentence.contains("onde")) {
            return "Onde " + sentence.substring(sentence.indexOf("onde") + 4, Math.min(sentence.length(), 50)) + "?";
        }

        // Pergunta genérica
        return "Sobre " + sentence.substring(0, Math.min(sentence.length(), 30)) + "?";
    }

    /**
     * Faz o parse da resposta Q&A do LLM
     */
    private List<QuestionAnswer> parseQAResponse(String response) {
        List<QuestionAnswer> qaList = new ArrayList<>();
        String[] blocks = response.split("\\n\\s*\\n"); // Separar por linhas duplas

        for (String block : blocks) {
            String[] lines = block.trim().split("\\n");
            String question = null;
            StringBuilder answer = new StringBuilder();

            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("Q:")) {
                    question = line.substring(2).trim();
                } else if (line.startsWith("A:")) {
                    answer.append(line.substring(2).trim());
                } else if (!line.isEmpty() && question != null) {
                    // Continuar resposta em múltiplas linhas
                    if (answer.length() > 0) {
                        answer.append(" ");
                    }
                    answer.append(line);
                }
            }

            if (question != null && answer.length() > 0) {
                qaList.add(new QuestionAnswer(question, answer.toString().trim()));
            }
        }

        return qaList;
    }

    /**
     * Verifica se o serviço de LLM está disponível
     */
    public boolean isLLMServiceAvailable() {
        return llmService != null;
    }

    /**
     * Obtém estatísticas do summarizer
     */
    public java.util.Map<String, Object> getSummarizerStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("llm_service_available", llmService != null);
        stats.put("default_summary_length", DEFAULT_SUMMARY_LENGTH);
        stats.put("service_class", this.getClass().getSimpleName());

        if (llmService != null) {
            try {
                stats.put("available_models", llmService.getRegisteredModelNames());
            } catch (LLMException e) {
                logger.debug("Could not retrieve model names: {}", e.getMessage());
            }
        }

        return stats;
    }
}