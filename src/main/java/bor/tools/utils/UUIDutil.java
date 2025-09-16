package bor.tools.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.UUID;

/**
 * Utility class for generating UUIDs from text.
 */
public class UUIDutil {
	
	/**
	 * The UUID namespace for generating UUIDs from text.
	 */
	public static final String UUID_NAMESPACE = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

	private UUIDutil() {
	    // Utility class
	}

	
	private static String cleanText(String texto) {
		if (texto == null || texto.isEmpty()) {
			return "00000000-0000-0000-0000-000000000000";
		}
		return texto.trim().replace('\n',' ').replaceAll("\\s+", "").toLowerCase();
	}
	/**
	 * Generate a UUID version 5 from a text
	 * @param texto
	 * @return
	 */	
	public static String generateUUIDFromText(String texto) {
		if (texto == null || texto.isEmpty()) {
			return "00000000-0000-0000-0000-000000000000";
		}
		
		texto = cleanText(texto);
		try {
			// Define a namespace UUID (using a fixed UUID for documents)
			UUID namespace = UUID.fromString(UUID_NAMESPACE); // A fixed namespace UUID

			// Generate version 5 UUID (SHA-1) using namespace and text content
			byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
			UUID documentUUID = generateUUIDv5(namespace, bytes);

			// Set the identificador field
			return documentUUID.toString();

		} catch (Exception e) {
			throw new RuntimeException("Error generating UUID from text: " + e.getMessage(), e);
		}
	}
 
	/**
	 * Generate a version 5 UUID (SHA-1) using a namespace and a name
	 * @param namespace
	 * @param name
	 * @return
	 */
	private static UUID generateUUIDv5(UUID namespace, byte[] name) {
	    MessageDigest sha1;
	    try {
	        sha1 = MessageDigest.getInstance("SHA-1");
	    } catch (NoSuchAlgorithmException e) {
	        throw new RuntimeException("SHA-1 not available", e);
	    }

	    // Add namespace
	    sha1.update(toBytes(namespace));
	    // Add name
	    sha1.update(name);
	    byte[] hash = sha1.digest();

	    // Set version 5 and variant bits
	    hash[6] &= 0x0f;
	    hash[6] |= 0x50; // version 5
	    hash[8] &= 0x3f;
	    hash[8] |= 0x80; // variant 2

	    long msb = 0;
	    long lsb = 0;
	    for (int i = 0; i < 8; i++)
	        msb = (msb << 8) | (hash[i] & 0xff);
	    for (int i = 8; i < 16; i++)
	        lsb = (lsb << 8) | (hash[i] & 0xff);

	    return new UUID(msb, lsb);
	}

	/**
	 * Convert a UUID to a byte array
	 * @param uuid
	 * @return
	 */
	private static byte[] toBytes(UUID uuid) {
	    byte[] out = new byte[16];
	    long msb = uuid.getMostSignificantBits();
	    long lsb = uuid.getLeastSignificantBits();
	    for (int i = 0; i < 8; i++)
	        out[i] = (byte) ((msb >> ((7 - i) * 8)) & 0xff);
	    for (int i = 8; i < 16; i++)
	        out[i] = (byte) ((lsb >> ((15 - i) * 8)) & 0xff);
	    return out;
	}

	/**
	 * Generate a SHA-256 hash from a text.
	 * São exluidos espaços em branco e caracteres especiais.
	 * @param texto
	 * @return
	 */
	public static String generateSHA256FromText(String texto) {
		MessageDigest sha;
	    try {
	        sha = MessageDigest.getInstance("SHA-1");
	        texto = cleanText(texto);
	        sha.update(texto.getBytes(StandardCharsets.UTF_8));
	        byte[] digest = sha.digest();
	        
	        StringBuilder sb = new StringBuilder();
	        for (byte b : digest) {
	            sb.append(String.format("%02x", b));
	        }	       
	        return sb.toString();	        
	    } catch (NoSuchAlgorithmException e) {
	        throw new RuntimeException("SHA-256 not available", e);
	    }
	}
	
}
