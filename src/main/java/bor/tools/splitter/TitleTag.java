package bor.tools.splitter;

import lombok.Data;

/**
 * A simple class to hold title tags and their titles.
 * Examples:<br>
 *  <li>tag = "h1", title = "Chapter 1" - HTML style
 *  <li>tag = "##", title = "Section 1.1" - Markdown style
 * 
 */
@Data
public class TitleTag {

    /**
     * The tag representing the title level (e.g., "h1", "##").
     */
    private String tag;
    /**
     * The actual title text.
     */
    private String title;
    /**
     * The level of the title (e.g., 1 for h1 or #, 2 for h2 or ##).
     */
    private int level;
    /**
     * Optional the position of the title in the document (e.g., line index).
     */
    private Integer position;
    
    /**
     * Optional the amount of lines belonging to this title.
     */
    private Integer linesLength;
    
    /**
     * Constructor
     */
    public TitleTag() {	
    }
    
    /**
     * The position of the title in the document (e.g., line index).
     * @return
     */
    public Integer getPosition() {
	return position;
    }
    
    public void setPosition(Integer position) {
	this.position = position;
    }

}
