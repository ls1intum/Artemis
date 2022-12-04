package de.tum.in.www1.artemis.service.connectors.lti.oauth.signature;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import de.tum.in.www1.artemis.service.connectors.lti.oauth.*;

/**
 * A pair of algorithms for computing and verifying an OAuth digital signature.
 * <p>
 * Static methods of this class implement a registry of signature methods. It's
 * pre-populated with the standard OAuth algorithms. Applications can replace
 * them or add new ones.
 *
 * @author John Kristian
 */
public abstract class OAuthSignatureMethod {

    /**
     * Add a signature to the message
     * @param message
     * @throws OAuthException
     * @throws IOException
     * @throws URISyntaxException
     */
    public void sign(OAuthMessage message) throws OAuthException, IOException, URISyntaxException {
        message.addParameter(new OAuth.Parameter("oauth_signature", getSignature(message)));
    }

    /**
     * Check whether the message has a valid signature.
     * @param message
     * @throws IOException
     * @throws OAuthException   the signature is invalid
     * @throws URISyntaxException
     */
    public void validate(OAuthMessage message) throws IOException, OAuthException, URISyntaxException {
        message.requireParameters("oauth_signature");
        String signature = message.getSignature();
        String baseString = getBaseString(message);
        if (!isValid(signature, baseString)) {
            OAuthProblemException problem = new OAuthProblemException("signature_invalid");
            problem.setParameter("oauth_signature", signature);
            problem.setParameter("oauth_signature_base_string", baseString);
            problem.setParameter("oauth_signature_method", message.getSignatureMethod());
            throw problem;
        }
    }

    protected String getSignature(OAuthMessage message) throws OAuthException, IOException, URISyntaxException {
        String baseString = getBaseString(message);
        return getSignature(baseString);
    }

    protected void initialize(String name, OAuthAccessor accessor) throws OAuthException {
        String secret = accessor.consumer.consumerSecret;
        if (name.endsWith(_ACCESSOR)) {
            // This code supports the 'Accessor Secret' extensions
            // described in http://oauth.pbwiki.com/AccessorSecret
            final String key = OAuthConsumer.ACCESSOR_SECRET;
            Object accessorSecret = accessor.getProperty(key);
            if (accessorSecret == null) {
                accessorSecret = accessor.consumer.getProperty(key);
            }
            if (accessorSecret != null) {
                secret = accessorSecret.toString();
            }
        }
        if (secret == null) {
            secret = "";
        }
        setConsumerSecret(secret);
    }

    public static final String _ACCESSOR = "-Accessor";

    /** Compute the signature for the given base string. */
    protected abstract String getSignature(String baseString) throws OAuthException;

    /** Decide whether the signature is valid. */
    protected abstract boolean isValid(String signature, String baseString) throws OAuthException;

    private String consumerSecret;

    private String tokenSecret;

    protected String getConsumerSecret() {
        return consumerSecret;
    }

    protected void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    public String getTokenSecret() {
        return tokenSecret;
    }

    public void setTokenSecret(String tokenSecret) {
        this.tokenSecret = tokenSecret;
    }

    /**
     *
     * @param message
     * @return the base string of the given message
     * @throws IOException
     * @throws URISyntaxException
     */
    public static String getBaseString(OAuthMessage message) throws IOException, URISyntaxException {
        List<Map.Entry<String, String>> parameters;
        String url = message.URL;
        int q = url.indexOf('?');
        if (q < 0) {
            parameters = message.getParameters();
        }
        else {
            // Combine the URL query string with the other parameters:
            parameters = new ArrayList<>();
            parameters.addAll(OAuth.decodeForm(message.URL.substring(q + 1)));
            parameters.addAll(message.getParameters());
            url = url.substring(0, q);
        }
        return OAuth.percentEncode(message.method.toUpperCase()) + '&' + OAuth.percentEncode(normalizeUrl(url)) + '&' + OAuth.percentEncode(normalizeParameters(parameters));
    }

    protected static String normalizeUrl(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String scheme = uri.getScheme().toLowerCase();
        String authority = uri.getAuthority().toLowerCase();
        boolean dropPort = (scheme.equals("http") && uri.getPort() == 80) || (scheme.equals("https") && uri.getPort() == 443);
        if (dropPort) {
            // find the last : in the authority
            int index = authority.lastIndexOf(":");
            if (index >= 0) {
                authority = authority.substring(0, index);
            }
        }
        String path = uri.getRawPath();
        if (path == null || path.length() == 0) {
            path = "/"; // conforms to RFC 2616 section 3.2.2
        }
        // we know that there is no query and no fragment here.
        return scheme + "://" + authority + path;
    }

    protected static String normalizeParameters(Collection<? extends Map.Entry<String, String>> parameters) throws IOException {
        if (parameters == null) {
            return "";
        }
        List<OAuthSignatureMethod.ComparableParameter> p = new ArrayList<>(parameters.size());
        for (Map.Entry<String, String> parameter : parameters) {
            if (!"oauth_signature".equals(parameter.getKey())) {
                p.add(new OAuthSignatureMethod.ComparableParameter(parameter));
            }
        }
        Collections.sort(p);
        return OAuth.formEncode(getParameters(p));
    }

    /**
     * Determine whether the given strings contain the same sequence of
     * characters. The implementation discourages a <a
     * href="http://codahale.com/a-lesson-in-timing-attacks/">timing attack</a>.
     * @param s1
     * @param s2
     * @return whether s1 and s2 are equal
     */
    public static boolean equals(String s1, String s2) {
        if (s1 == null)
            return s2 == null;
        else if (s2 == null)
            return false;
        else if (s2.length() == 0)
            return s1.length() == 0;
        char[] a = s1.toCharArray();
        char[] b = s2.toCharArray();
        char diff = (char) ((a.length == b.length) ? 0 : 1);
        int j = 0;
        for (char c : a) {
            diff |= c ^ b[j];
            j = (j + 1) % b.length;
        }
        return diff == 0;
    }

    /**
     * Determine whether the given arrays contain the same sequence of bytes.
     * The implementation discourages b1 <b1
     * href="http://codahale.com/a-lesson-in-timing-attacks/">timing attack</b1>.
     * @param b1
     * @param b2
     * @return whether b1 and b2 are equal
     */
    public static boolean equals(byte[] b1, byte[] b2) {
        if (b1 == null)
            return b2 == null;
        else if (b2 == null)
            return false;
        else if (b2.length == 0)
            return b1.length == 0;
        byte diff = (byte) ((b1.length == b2.length) ? 0 : 1);
        int j = 0;
        for (byte value : b1) {
            diff |= value ^ b2[j];
            j = (j + 1) % b2.length;
        }
        return diff == 0;
    }

    /**
     *
     * @param string
     * @return a byte array for the given base64 string
     */
    public static byte[] decodeBase64(String string) {
        return BASE64.decode(string.getBytes(StandardCharsets.ISO_8859_1)); // default charset used for BASE64
    }

    /**
     *
     * @param bytes
     * @return a base64 encoded string for the given byte array
     */
    public static String base64Encode(byte[] bytes) {
        byte[] encodedBytes = BASE64.encode(bytes);
        return new String(encodedBytes, StandardCharsets.ISO_8859_1); // default charset used for BASE64
    }

    /**
     * The character encoding used for base64. Arguably US-ASCII is more
     * accurate, but this one decodes all byte values unambiguously.
     */
    private static final String BASE64_ENCODING = "ISO-8859-1";

    private static final Base64 BASE64 = new Base64();

    public static OAuthSignatureMethod newSigner(OAuthMessage message, OAuthAccessor accessor) throws OAuthException {
        message.requireParameters(OAuth.OAUTH_SIGNATURE_METHOD);
        OAuthSignatureMethod signer = newInstance(message.getSignatureMethod(), accessor);
        signer.setTokenSecret(accessor.tokenSecret);
        return signer;
    }

    /**
     * The factory for signature methods
     * @param name
     * @param accessor
     * @return creates a new instance of OAuthSignatureMethod based on the used algorithm
     * @throws OAuthException
     */
    public static OAuthSignatureMethod newInstance(String name, OAuthAccessor accessor) throws OAuthException {
        try {
            Class<? extends OAuthSignatureMethod> methodClass = NAME_TO_CLASS.get(name);
            if (methodClass != null) {
                OAuthSignatureMethod method = methodClass.getDeclaredConstructor().newInstance();
                method.initialize(name, accessor);
                return method;
            }
            OAuthProblemException problem = new OAuthProblemException(OAuth.Problems.SIGNATURE_METHOD_REJECTED);
            String acceptable = OAuth.percentEncode(NAME_TO_CLASS.keySet());
            if (acceptable.length() > 0) {
                problem.setParameter("oauth_acceptable_signature_methods", acceptable);
            }
            throw problem;
        }
        catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new OAuthException(e);
        }
    }

    /**
     * Subsequently, newInstance(name) will attempt to instantiate the given
     * class, with no constructor parameters.
     * @param name
     * @param clazz
     * @param <T>
     */
    public static <T extends OAuthSignatureMethod> void registerMethodClass(String name, Class<T> clazz) {
        if (clazz == null) {
            unregisterMethod(name);
        }
        else {
            NAME_TO_CLASS.put(name, clazz);
        }
    }

    public static void unregisterMethod(String name) {
        NAME_TO_CLASS.remove(name);
    }

    private static final Map<String, Class<? extends OAuthSignatureMethod>> NAME_TO_CLASS = new ConcurrentHashMap<>();
    static {
        registerMethodClass("HMAC-SHA1", HMAC_SHA1.class);
        registerMethodClass("PLAINTEXT", PLAINTEXT.class);
        registerMethodClass("RSA-SHA1", RSA_SHA1.class);
        registerMethodClass("HMAC-SHA1" + _ACCESSOR, HMAC_SHA1.class);
        registerMethodClass("PLAINTEXT" + _ACCESSOR, PLAINTEXT.class);
    }

    /** An efficiently sortable wrapper around a parameter. */
    private static class ComparableParameter implements Comparable<OAuthSignatureMethod.ComparableParameter> {

        ComparableParameter(Map.Entry<String, String> value) {
            this.value = value;
            String n = toString(value.getKey());
            String v = toString(value.getValue());
            this.key = OAuth.percentEncode(n) + ' ' + OAuth.percentEncode(v);
            // ' ' is used because it comes before any character
            // that can appear in a percentEncoded string.
        }

        final Map.Entry<String, String> value;

        private final String key;

        private static String toString(Object from) {
            return (from == null) ? null : from.toString();
        }

        public int compareTo(OAuthSignatureMethod.ComparableParameter that) {
            return this.key.compareTo(that.key);
        }

        @Override
        public String toString() {
            return key;
        }

    }

    /** Retrieve the original parameters from a sorted collection. */
    private static List<Map.Entry<String, String>> getParameters(Collection<OAuthSignatureMethod.ComparableParameter> parameters) {
        if (parameters == null) {
            return null;
        }
        List<Map.Entry<String, String>> list = new ArrayList<>(parameters.size());
        for (OAuthSignatureMethod.ComparableParameter parameter : parameters) {
            list.add(parameter.value);
        }
        return list;
    }

}
