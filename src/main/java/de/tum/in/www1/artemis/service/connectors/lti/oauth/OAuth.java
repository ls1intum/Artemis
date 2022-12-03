package de.tum.in.www1.artemis.service.connectors.lti.oauth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Miscellaneous constants, methods and types.
 *
 * @author John Kristian
 */
public class OAuth {

    public static final String VERSION_1_0 = "1.0";

    /** The encoding used to represent characters as bytes. */
    public static final String ENCODING = "UTF-8";

    public static final String OAUTH_CONSUMER_KEY = "oauth_consumer_key";

    public static final String OAUTH_TOKEN = "oauth_token";

    public static final String OAUTH_TOKEN_SECRET = "oauth_token_secret";

    public static final String OAUTH_SIGNATURE_METHOD = "oauth_signature_method";

    public static final String OAUTH_SIGNATURE = "oauth_signature";

    public static final String OAUTH_TIMESTAMP = "oauth_timestamp";

    public static final String OAUTH_NONCE = "oauth_nonce";

    public static final String OAUTH_VERSION = "oauth_version";

    public static final String OAUTH_CALLBACK = "oauth_callback";

    /**
     * Strings used for <a href="http://wiki.oauth.net/ProblemReporting">problem
     * reporting</a>.
     */
    public static class Problems {

        public static final String VERSION_REJECTED = "version_rejected";

        public static final String PARAMETER_ABSENT = "parameter_absent";

        public static final String PARAMETER_REJECTED = "parameter_rejected";

        public static final String TIMESTAMP_REFUSED = "timestamp_refused";

        public static final String NONCE_USED = "nonce_used";

        public static final String SIGNATURE_METHOD_REJECTED = "signature_method_rejected";

        public static final String OAUTH_ACCEPTABLE_VERSIONS = "oauth_acceptable_versions";

        public static final String OAUTH_ACCEPTABLE_TIMESTAMPS = "oauth_acceptable_timestamps";

        public static final String OAUTH_PARAMETERS_ABSENT = "oauth_parameters_absent";

        public static final String OAUTH_PARAMETERS_REJECTED = "oauth_parameters_rejected";

        public static final String OAUTH_PROBLEM_ADVICE = "oauth_problem_advice";

    }

    private static final String characterEncoding = ENCODING;

    public static String decodeCharacters(byte[] from) {
        try {
            return new String(from, characterEncoding);
        }
        catch (UnsupportedEncodingException e) {
            System.err.println(e + "");
        }
        return new String(from);
    }

    public static byte[] encodeCharacters(String from) {
        try {
            return from.getBytes(characterEncoding);
        }
        catch (UnsupportedEncodingException e) {
            System.err.println(e + "");
        }
        return from.getBytes();
    }

    /**
     * Construct a form-urlencoded document containing the given sequence of
     * name/value pairs. Use OAuth percent encoding (not exactly the encoding
     * mandated by HTTP).
     */
    public static String formEncode(Iterable<? extends Map.Entry<String, String>> parameters) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        formEncode(parameters, b);
        return decodeCharacters(b.toByteArray());
    }

    /**
     * Write a form-urlencoded document into the given stream, containing the
     * given sequence of name/value pairs.
     */
    public static void formEncode(Iterable<? extends Map.Entry<String, String>> parameters, OutputStream into) throws IOException {
        if (parameters != null) {
            boolean first = true;
            for (Map.Entry<String, String> parameter : parameters) {
                if (first) {
                    first = false;
                }
                else {
                    into.write('&');
                }
                into.write(encodeCharacters(percentEncode(toString(parameter.getKey()))));
                into.write('=');
                into.write(encodeCharacters(percentEncode(toString(parameter.getValue()))));
            }
        }
    }

    /** Parse a form-urlencoded document. */
    public static List<OAuth.Parameter> decodeForm(String form) {
        List<OAuth.Parameter> list = new ArrayList<>();
        if (!isEmpty(form)) {
            for (String nvp : form.split("&")) {
                int equals = nvp.indexOf('=');
                String name;
                String value;
                if (equals < 0) {
                    name = decodePercent(nvp);
                    value = null;
                }
                else {
                    name = decodePercent(nvp.substring(0, equals));
                    value = decodePercent(nvp.substring(equals + 1));
                }
                list.add(new OAuth.Parameter(name, value));
            }
        }
        return list;
    }

    /** Construct a &-separated list of the given values, percentEncoded. */
    public static String percentEncode(Iterable<?> values) {
        StringBuilder p = new StringBuilder();
        for (Object v : values) {
            if (p.length() > 0) {
                p.append("&");
            }
            p.append(OAuth.percentEncode(toString(v)));
        }
        return p.toString();
    }

    public static String percentEncode(String s) {
        if (s == null) {
            return "";
        }
        try {
            return URLEncoder.encode(s, ENCODING)
                    // OAuth encodes some characters differently:
                    .replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
            // This could be done faster with more hand-crafted code.
        }
        catch (UnsupportedEncodingException wow) {
            throw new RuntimeException(wow.getMessage(), wow);
        }
    }

    public static String decodePercent(String s) {
        try {
            return URLDecoder.decode(s, ENCODING);
            // This implements http://oauth.pbwiki.com/FlexibleDecoding
        }
        catch (java.io.UnsupportedEncodingException wow) {
            throw new RuntimeException(wow.getMessage(), wow);
        }
    }

    /**
     * Construct a Map containing a copy of the given parameters. If several
     * parameters have the same name, the Map will contain the first value,
     * only.
     */
    public static Map<String, String> newMap(Iterable<? extends Map.Entry<String, String>> from) {
        Map<String, String> map = new HashMap<>();
        if (from != null) {
            for (Map.Entry<String, String> f : from) {
                String key = toString(f.getKey());
                if (!map.containsKey(key)) {
                    map.put(key, toString(f.getValue()));
                }
            }
        }
        return map;
    }

    /** A name/value pair. */
    public static class Parameter implements Map.Entry<String, String> {

        public Parameter(String key, String value) {
            this.key = key;
            this.value = value;
        }

        private final String key;

        private String value;

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public String setValue(String value) {
            try {
                return this.value;
            }
            finally {
                this.value = value;
            }
        }

        @Override
        public String toString() {
            return percentEncode(getKey()) + '=' + percentEncode(getValue());
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            result = prime * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final OAuth.Parameter that = (OAuth.Parameter) obj;
            if (key == null) {
                if (that.key != null)
                    return false;
            }
            else if (!key.equals(that.key))
                return false;
            if (value == null) {
                return that.value == null;
            }
            else {
                return value.equals(that.value);
            }
        }
    }

    private static String toString(Object from) {
        return (from == null) ? null : from.toString();
    }

    public static boolean isEmpty(String str) {
        return (str == null) || (str.length() == 0);
    }
}
