package bor.tools.simplerag.util;

import bor.tools.simplellm.SERVICE_PROVIDER;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for LLMProviderParser utility class.
 *
 * Tests cover:
 * - Direct enum name matching
 * - Alias handling
 * - Name normalization
 * - Error cases
 * - Edge cases
 */
class LLMProviderParserTest {

    // ========== Direct Enum Name Tests ==========

    @ParameterizedTest
    @CsvSource({
        "LM_STUDIO, LM_STUDIO",
        "OPENAI, OPENAI",
        "OLLAMA, OLLAMA",
        "ANTHROPIC, ANTHROPIC"
    })
    void testParseProviderName_DirectEnumMatch(String input, SERVICE_PROVIDER expected) {
        SERVICE_PROVIDER result = LLMProviderParser.parseProviderName(input);
        assertEquals(expected, result);
    }

    // ========== Alias Tests ==========

    @ParameterizedTest
    @CsvSource({
        "LMSTUDIO, LM_STUDIO",
        "lm-studio, LM_STUDIO",
        "lm studio, LM_STUDIO",
        "GPT, OPENAI",
        "openai, OPENAI",
        "ollama, OLLAMA",
        "CLAUDE, ANTHROPIC",
        "anthropic, ANTHROPIC"
    })
    void testParseProviderName_Aliases(String input, SERVICE_PROVIDER expected) {
        SERVICE_PROVIDER result = LLMProviderParser.parseProviderName(input);
        assertEquals(expected, result);
    }

    // ========== Normalization Tests ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "lm_studio",
        "LM_STUDIO",
        "Lm_Studio",
        "lm-studio",
        "LM-STUDIO",
        "lm studio",
        "LM STUDIO",
        "  lm_studio  ",
        "\tlm_studio\n"
    })
    void testParseProviderName_Normalization_LMStudio(String input) {
        SERVICE_PROVIDER result = LLMProviderParser.parseProviderName(input);
        assertEquals(SERVICE_PROVIDER.LM_STUDIO, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "openai",
        "OPENAI",
        "OpenAI",
        "Open-AI",
        "open ai",
        "  openai  ",
        "gpt",
        "GPT",
        "Gpt"
    })
    void testParseProviderName_Normalization_OpenAI(String input) {
        SERVICE_PROVIDER result = LLMProviderParser.parseProviderName(input);
        assertEquals(SERVICE_PROVIDER.OPENAI, result);
    }

    // ========== Suffix Removal Tests ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "LM_STUDIOLLMService",
        "LM_STUDIO.java",
        "LM_STUDIOjava",
        "LM_STUDIOLLMService.java"
    })
    void testParseProviderName_SuffixRemoval(String input) {
        SERVICE_PROVIDER result = LLMProviderParser.parseProviderName(input);
        assertEquals(SERVICE_PROVIDER.LM_STUDIO, result);
    }

    // ========== Error Case Tests ==========

    @ParameterizedTest
    @NullAndEmptySource
    void testParseProviderName_NullOrEmpty_ThrowsException(String input) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> LLMProviderParser.parseProviderName(input)
        );
        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UNKNOWN_PROVIDER",
        "FAKE_LLM",
        "NOT_A_PROVIDER",
        "123",
        "special@chars#here"
    })
    void testParseProviderName_UnknownProvider_ThrowsException(String input) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> LLMProviderParser.parseProviderName(input)
        );
        assertTrue(exception.getMessage().contains("Unknown LLM provider"));
        assertTrue(exception.getMessage().contains(input));
    }

    // ========== isProviderSupported Tests ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "LM_STUDIO",
        "lm-studio",
        "OPENAI",
        "gpt",
        "OLLAMA",
        "ANTHROPIC",
        "claude"
    })
    void testIsProviderSupported_ValidProviders_ReturnsTrue(String input) {
        assertTrue(LLMProviderParser.isProviderSupported(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "UNKNOWN_PROVIDER",
        "FAKE_LLM",
        "NOT_A_PROVIDER"
    })
    void testIsProviderSupported_InvalidProviders_ReturnsFalse(String input) {
        assertFalse(LLMProviderParser.isProviderSupported(input));
    }

    // ========== getNormalizedName Tests ==========

    @ParameterizedTest
    @CsvSource({
        "lm-studio, LM_STUDIO",
        "lm studio, LM_STUDIO",
        "  openai  , OPENAI",
        "Open-AI, OPEN_AI",
        "OllamaLLMService, OLLAMA",
        "anthropic.java, ANTHROPIC"
    })
    void testGetNormalizedName_ReturnsNormalizedForm(String input, String expected) {
        String result = LLMProviderParser.getNormalizedName(input);
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testGetNormalizedName_NullOrEmpty_ThrowsException(String input) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> LLMProviderParser.getNormalizedName(input)
        );
        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }

    // ========== Utility Class Tests ==========

    @Test
    void testConstructor_ThrowsException() {
        Exception exception = assertThrows(Exception.class, () -> {
            // Use reflection to try to instantiate
            var constructor = LLMProviderParser.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        });
        // Check that the root cause is UnsupportedOperationException
        Throwable cause = exception.getCause();
        assertTrue(cause instanceof UnsupportedOperationException,
                  "Expected UnsupportedOperationException but got: " + cause.getClass().getName());
    }

    // ========== Edge Cases ==========

    @Test
    void testParseProviderName_MultipleSpaces_HandledCorrectly() {
        SERVICE_PROVIDER result = LLMProviderParser.parseProviderName("lm    studio");
        assertEquals(SERVICE_PROVIDER.LM_STUDIO, result);
    }

    @Test
    void testParseProviderName_MixedCaseWithSuffixes_HandledCorrectly() {
        SERVICE_PROVIDER result = LLMProviderParser.parseProviderName("LmStudioLLMService.java");
        assertEquals(SERVICE_PROVIDER.LM_STUDIO, result);
    }

    @Test
    void testParseProviderName_LeadingTrailingWhitespace_HandledCorrectly() {
        SERVICE_PROVIDER result = LLMProviderParser.parseProviderName("  \t OPENAI \n  ");
        assertEquals(SERVICE_PROVIDER.OPENAI, result);
    }

    // ========== Documentation Tests ==========

    @Test
    void testParseProviderName_AllSupportedProviders_CanBeParsed() {
        // Test that all currently supported providers can be parsed
        assertDoesNotThrow(() -> {
            LLMProviderParser.parseProviderName("LM_STUDIO");
            LLMProviderParser.parseProviderName("OPENAI");
            LLMProviderParser.parseProviderName("OLLAMA");
            LLMProviderParser.parseProviderName("ANTHROPIC");
        });
    }

    @Test
    void testParseProviderName_AllAliases_CanBeParsed() {
        // Test that all documented aliases work
        assertDoesNotThrow(() -> {
            LLMProviderParser.parseProviderName("LMSTUDIO");
            LLMProviderParser.parseProviderName("GPT");
            LLMProviderParser.parseProviderName("CLAUDE");
        });
    }

    // ========== Error Message Quality Tests ==========

    @Test
    void testParseProviderName_UnknownProvider_ErrorMessageContainsOriginalName() {
        String unknownProvider = "MY_CUSTOM_PROVIDER";
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> LLMProviderParser.parseProviderName(unknownProvider)
        );
        assertTrue(exception.getMessage().contains(unknownProvider),
                  "Error message should contain original provider name");
    }

    @Test
    void testParseProviderName_UnknownProvider_ErrorMessageContainsNormalizedName() {
        String input = "my-custom-provider";
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> LLMProviderParser.parseProviderName(input)
        );
        assertTrue(exception.getMessage().contains("MY_CUSTOM_PROVIDER"),
                  "Error message should contain normalized name");
    }

    @Test
    void testParseProviderName_UnknownProvider_ErrorMessageListsSupportedProviders() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> LLMProviderParser.parseProviderName("UNKNOWN")
        );
        String message = exception.getMessage();
        assertTrue(message.contains("Supported providers:"),
                  "Error message should list supported providers");
        assertTrue(message.contains("LM_STUDIO"));
        assertTrue(message.contains("OPENAI"));
        assertTrue(message.contains("OLLAMA"));
        assertTrue(message.contains("ANTHROPIC"));
    }
}
