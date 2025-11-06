package bor.tools.utils;

import java.util.Map;

import lombok.Data;

/**
 * Classe para WikiParse.
 *
 * @see RagUtils#wikiArticleXtract(String, String)
 */

@Data
public class WikiParse {

    /**
     * Este é o holder do conteudo
     */
    WikiParse parse;

    public String title;
    Integer pageid;
    public Map<String,Object> text;

    public Map<String,Object> getText() {
        if (this.text != null)
            return text;
        else if (parse != null) {
            return parse.text;
        }
        return null;
    }

    public String getTitle() {
        if (this.title != null)
            return title;
        if (parse != null) {
            return parse.title;
        }
        return null;
    }

    public Integer getPageId() {
        if (this.pageid != null)
            return pageid;
        if (parse != null) {
            return parse.pageid;
        }
        return null;
    }

    @Override
    public String toString() {
        return "WikiParse [title=" + title + ", pageid=" + pageid + ", text=" + text + "]";
    }

}
