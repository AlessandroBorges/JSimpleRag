package bor.tools.splitter;

import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Wrapper para links web.
 * Conteudo com texto e, opcionalmente, uma URL associada
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebLinks implements Serializable {

	/**
	 *
	 */
	private static final long serialVersionUID = -883994976798082258L;


	/**
	 * Texto do link
	 */
	private String texto;
	/**
	 * URL do link
	 */
	private String url;

}