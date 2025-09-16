package bor.tools.utils;
import java.util.zip.Checksum;

/**
 * CRC64 checksum implementation.
 * 
 * <p>
 * This class implements the CRC64 checksum algorithm, which is a 64-bit cyclic
 * redundancy check. It provides methods to update the checksum with bytes, get
 * the current checksum value, and reset the checksum.
 * <p>
 * 
 * <p>
 * Reference:
 * <a href="https://en.wikipedia.org/wiki/Cyclic_redundancy_check">Cyclic
 * Redundancy Check - Wikipedia</a>
 * </p>
 * 
 * <p>
 * This implementation uses a lookup table for efficiency.
 * <p>
 * 
 * <pre>
 * Example usage: 
 * 
 * String input = "Mestre Skywalker"; 
 * byte[] bytes = input.getBytes();
 * 
 * Checksum crc64 = new CRC64(); 
 * crc64.update(bytes, 0, bytes.length);  
 * long value = crc64.getValue(); 
 * System.out.println("CRC64 (hex): " + CRC64.toHex(value));
 * 
 * </pre
 * 
 * 
 * @author aless
 */
public class CRC64 implements Checksum {

    private static final long[] LOOKUP_TABLE = new long[256];
    private static final long POLY = 0x42F0E1EBA9EA3693L; // CRC-64-ECMA-182
    private long crc = 0L;

    static {
        for (int i = 0; i < 256; i++) {
            long part = i;
            for (int j = 0; j < 8; j++) {
                if ((part & 1L) != 0) {
                    part = (part >>> 1) ^ POLY;
                } else {
                    part = (part >>> 1);
                }
            }
            LOOKUP_TABLE[i] = part;
        }
    }

    @Override
    public void update(int b) {
        crc = LOOKUP_TABLE[(int) (b ^ crc) & 0xFF] ^ (crc >>> 8);
    }

    @Override
    public void update(byte[] b, int off, int len) {
        for (int i = off; i < off + len; i++) {
            crc = LOOKUP_TABLE[(b[i] ^ (int) crc) & 0xFF] ^ (crc >>> 8);
        }
    }

    @Override
    public long getValue() {
        return crc;
    }

    @Override
    public void reset() {
        crc = 0L;
    }

    public static String toHex(long crc) {
        return String.format("%016X", crc);
    }
}
