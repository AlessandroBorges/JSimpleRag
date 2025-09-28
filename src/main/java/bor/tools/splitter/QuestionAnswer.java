
package bor.tools.splitter;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents a question-answer pair generated from text.
 */
@Getter
@Setter
@ToString
public class QuestionAnswer {

    public QuestionAnswer() {
    }

    public QuestionAnswer(String question, String answer) {
	this.question = question;
	this.answer = answer;
    }

    /**
     * The Question
     */
    private String question;
    /**
     * The Answer
     */
    private String answer;

}
