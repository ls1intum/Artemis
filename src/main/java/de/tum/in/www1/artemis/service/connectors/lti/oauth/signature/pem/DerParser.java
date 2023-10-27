package de.tum.in.www1.artemis.service.connectors.lti.oauth.signature.pem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

/**
 * A bare-minimum ASN.1 DER decoder, just having enough functions to
 * decode PKCS#1 private keys. Especially, it doesn't handle explicitly
 * tagged types with an outer tag.
 *
 * <p/>
 * This parser can only handle one layer. To parse nested constructs,
 * get a new parser for each layer using <code>Asn1Object.getParser()</code>.
 *
 * <p/>
 * There are many DER decoders in JRE but using them will tie this
 * program to a specific JCE/JVM.
 *
 */
class DerParser {

    // Constructed Flag
    public static final int CONSTRUCTED = 0x20;

    // Tag and data types
    public static final int ANY = 0x00;

    public static final int INTEGER = 0x02;

    public static final int NULL = 0x05;

    public static final int SEQUENCE = 0x10;

    public static final int NUMERIC_STRING = 0x12;

    public static final int PRINTABLE_STRING = 0x13;

    public static final int VIDEOTEX_STRING = 0x15;

    public static final int IA5_STRING = 0x16;

    public static final int GRAPHIC_STRING = 0x19;

    public static final int ISO646_STRING = 0x1A;

    public static final int GENERAL_STRING = 0x1B;

    public static final int UTF8_STRING = 0x0C;

    public static final int UNIVERSAL_STRING = 0x1C;

    public static final int BMP_STRING = 0x1E;

    protected InputStream in;

    /**
     * Create a new DER decoder from an input stream.
     *
     * @param in
     *               The DER encoded stream
     */
    public DerParser(InputStream in) {
        this.in = in;
    }

    /**
     * Create a new DER decoder from a byte array.
     */
    public DerParser(byte[] bytes) {
        this(new ByteArrayInputStream(bytes));
    }

    /**
     * Read next object. If it's constructed, the value holds
     * encoded content and it should be parsed by a new
     * parser from <code>Asn1Object.getParser</code>.
     *
     * @return A object
     */
    public Asn1Object read() throws IOException {
        int tag = in.read();

        if (tag == -1)
            throw new IOException("Invalid DER: stream too short, missing tag"); //$NON-NLS-1$

        int length = getLength();

        byte[] value = new byte[length];
        int n = in.read(value);
        if (n < length)
            throw new IOException("Invalid DER: stream too short, missing value"); //$NON-NLS-1$

        return new Asn1Object(tag, length, value);
    }

    /**
     * Decode the length of the field. Can only support length
     * encoding up to 4 octets.
     *
     * <p/>
     * In BER/DER encoding, length can be encoded in 2 forms,
     * <ul>
     * <li>Short form. One octet. Bit 8 has value "0" and bits 7-1
     * give the length.
     * <li>Long form. Two to 127 octets (only 4 is supported here).
     * Bit 8 of first octet has value "1" and bits 7-1 give the
     * number of additional length octets. Second and following
     * octets give the length, base 256, most significant digit first.
     * </ul>
     *
     * @return The length as integer
     */
    private int getLength() throws IOException {

        int i = in.read();
        if (i == -1)
            throw new IOException("Invalid DER: length missing"); //$NON-NLS-1$

        // A single byte short length
        if ((i & ~0x7F) == 0)
            return i;

        int num = i & 0x7F;

        // We can't handle length longer than 4 bytes
        if (i == 0xFF || num > 4)
            throw new IOException("Invalid DER: length field too big (" //$NON-NLS-1$
                    + i + ")"); //$NON-NLS-1$

        byte[] bytes = new byte[num];
        int n = in.read(bytes);
        if (n < num)
            throw new IOException("Invalid DER: length too short"); //$NON-NLS-1$

        return new BigInteger(1, bytes).intValue();
    }

}
