package de.tum.in.www1.artemis.service.connectors.lti.oauth.signature.pem;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

class Asn1Object {

    protected final int type;

    protected final int length;

    protected final byte[] value;

    protected final int tag;

    /**
     * Construct a ASN.1 TLV. The TLV could be either a
     * constructed or primitive entity.
     *
     * <p/>
     * The first byte in DER encoding is made of following fields,
     *
     * <pre>
     *-------------------------------------------------
     *|Bit 8|Bit 7|Bit 6|Bit 5|Bit 4|Bit 3|Bit 2|Bit 1|
     *-------------------------------------------------
     *|  Class    | CF  |     +      Type             |
     *-------------------------------------------------
     * </pre>
     * <ul>
     * <li>Class: Universal, Application, Context or Private
     * <li>CF: Constructed flag. If 1, the field is constructed.
     * <li>Type: This is actually called tag in ASN.1. It
     * indicates data type (Integer, String) or a construct
     * (sequence, choice, set).
     * </ul>
     *
     * @param tag    Tag or Identifier
     * @param length Length of the field
     * @param value  Encoded octet string for the field.
     */
    public Asn1Object(int tag, int length, byte[] value) {
        this.tag = tag;
        this.type = tag & 0x1F;
        this.length = length;
        this.value = value;
    }

    public int getType() {
        return type;
    }

    public int getLength() {
        return length;
    }

    public byte[] getValue() {
        return value;
    }

    public boolean isConstructed() {
        return (tag & DerParser.CONSTRUCTED) == DerParser.CONSTRUCTED;
    }

    /**
     * For constructed field, return a parser for its content.
     *
     * @return A parser for the construct.
     */
    public DerParser getParser() throws IOException {
        if (!isConstructed())
            throw new IOException("Invalid DER: can't parse primitive entity"); //$NON-NLS-1$

        return new DerParser(value);
    }

    /**
     * Get the value as integer
     *
     * @return BigInteger
     */
    public BigInteger getInteger() throws IOException {
        if (type != DerParser.INTEGER)
            throw new IOException("Invalid DER: object is not integer"); //$NON-NLS-1$

        return new BigInteger(value);
    }

    /**
     * Get value as string. Most strings are treated as Latin-1.
     *
     * @return Java string
     */
    public String getString() throws IOException {
        var charset = switch (type) {
            // Not all are Latin-1 but it's the closest thing
            case DerParser.NUMERIC_STRING, DerParser.PRINTABLE_STRING, DerParser.VIDEOTEX_STRING, DerParser.IA5_STRING, DerParser.GRAPHIC_STRING, DerParser.ISO646_STRING,
                    DerParser.GENERAL_STRING ->
                StandardCharsets.ISO_8859_1;
            case DerParser.BMP_STRING -> StandardCharsets.UTF_16BE;
            case DerParser.UTF8_STRING -> StandardCharsets.UTF_8;
            case DerParser.UNIVERSAL_STRING -> throw new IOException("Invalid DER: can't handle UCS-4 string");
            default -> throw new IOException("Invalid DER: object is not a string");
        };
        return new String(value, charset);
    }
}
