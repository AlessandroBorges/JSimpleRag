package bor.tools.splitter;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Represents a question-answer pair generated from text.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionAnswer {

    /**
     * The Question
     */
    private String question;
    /**
     * The Answer
     */
    private String answer;

}