package bor.tools.splitter;

import bor.tools.simplerag.dto.DocumentoDTO;

/**
 * Interface para verificar se um artefato existe.
 * @param <T>
 */
public interface ExistsArtefato<T> {
	/**
	 * Verifica se um artefato existe na base de dados.
	 *
	 * @param doc a ser verificado
	 * @return
	 */
	public boolean exists(DocumentoDTO doc);
}
