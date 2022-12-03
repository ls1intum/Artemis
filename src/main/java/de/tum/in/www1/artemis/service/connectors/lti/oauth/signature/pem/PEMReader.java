package de.tum.in.www1.artemis.service.connectors.lti.oauth.signature.pem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import de.tum.in.www1.artemis.service.connectors.lti.oauth.signature.OAuthSignatureMethod;

/**
 * This class convert PEM into byte array. The begin marker
 * is saved and it can be used to determine the type of the
 * PEM file.
 */
public class PEMReader {

    // Begin markers for all supported PEM files
    public static final String PRIVATE_PKCS1_MARKER = "-----BEGIN RSA PRIVATE KEY-----";

    public static final String PRIVATE_PKCS8_MARKER = "-----BEGIN PRIVATE KEY-----";

    public static final String CERTIFICATE_X509_MARKER = "-----BEGIN CERTIFICATE-----";

    public static final String PUBLIC_X509_MARKER = "-----BEGIN PUBLIC KEY-----";

    private static final String BEGIN_MARKER = "-----BEGIN ";

    private final InputStream stream;

    private byte[] derBytes;

    private String beginMarker;

    public PEMReader(InputStream inStream) throws IOException {
        stream = inStream;
        readFile();
    }

    public byte[] getDerBytes() {
        return derBytes;
    }

    public String getBeginMarker() {
        return beginMarker;
    }

    /**
     * Read the PEM file and save the DER encoded octet
     * stream and begin marker.
     */
    protected void readFile() throws IOException {

        String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            while ((line = reader.readLine()) != null) {
                if (line.contains(BEGIN_MARKER)) {
                    beginMarker = line.trim();
                    String endMarker = beginMarker.replace("BEGIN", "END");
                    derBytes = readBytes(reader, endMarker);
                    return;
                }
            }
            throw new IOException("Invalid PEM file: no begin marker");
        }
    }

    /**
     * Read the lines between BEGIN and END marker and convert
     * the Base64 encoded content into binary byte array.
     *
     * @return DER encoded octet stream
     */
    private byte[] readBytes(BufferedReader reader, String endMarker) throws IOException {
        String line;
        StringBuilder buf = new StringBuilder();

        while ((line = reader.readLine()) != null) {
            if (line.contains(endMarker)) {

                return OAuthSignatureMethod.decodeBase64(buf.toString());
            }

            buf.append(line.trim());
        }

        throw new IOException("Invalid PEM file: No end marker");
    }
}
