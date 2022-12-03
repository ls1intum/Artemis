package de.tum.in.www1.artemis.service.connectors.lti.oauth;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import de.tum.in.www1.artemis.service.connectors.lti.oauth.signature.OAuthSignatureMethod;

/**
 * A simple OAuthValidator, which checks the version, whether the timestamp is
 * close to now, the nonce hasn't been used before and the signature is valid.
 * Each check may be overridden.
 * <p>
 * This implementation is less than industrial strength:
 * <ul>
 * <li>Duplicate nonces won't be reliably detected by a service provider running
 * in multiple processes, since the used nonces are stored in memory.</li>
 * <li>The collection of used nonces is a synchronized choke point</li>
 * <li>The used nonces may occupy lots of memory, although you can minimize this
 * by calling releaseGarbage periodically.</li>
 * <li>The range of acceptable timestamps can't be changed, and there's no
 * system for increasing the range smoothly.</li>
 * <li>Correcting the clock backward may allow duplicate nonces.</li>
 * </ul>
 * For a big service provider, it might be better to store used nonces in a
 * database.
 *
 * @author Dirk Balfanz
 * @author John Kristian
 */
public class SimpleOAuthValidator {

    /** The default maximum age of timestamps is 5 minutes. */
    public static final long DEFAULT_MAX_TIMESTAMP_AGE = 5 * 60 * 1000L;

    public static final long DEFAULT_TIMESTAMP_WINDOW = DEFAULT_MAX_TIMESTAMP_AGE;

    /**
     * Names of parameters that may not appear twice in a valid message.
     * This limitation is specified by OAuth Core <a
     * href="http://oauth.net/core/1.0#anchor7">section 5</a>.
     */
    public static final Set<String> SINGLE_PARAMETERS = constructSingleParameters();

    private static Set<String> constructSingleParameters() {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, OAuth.OAUTH_CONSUMER_KEY, OAuth.OAUTH_TOKEN, OAuth.OAUTH_TOKEN_SECRET, OAuth.OAUTH_CALLBACK, OAuth.OAUTH_SIGNATURE_METHOD, OAuth.OAUTH_SIGNATURE,
                OAuth.OAUTH_TIMESTAMP, OAuth.OAUTH_NONCE, OAuth.OAUTH_VERSION);
        return Collections.unmodifiableSet(set);
    }

    /**
     * Construct a validator that rejects messages more than five minutes old or
     * with a OAuth version other than 1.0.
     */
    public SimpleOAuthValidator() {
        this(DEFAULT_TIMESTAMP_WINDOW, Double.parseDouble(OAuth.VERSION_1_0));
    }

    /**
     * Public constructor.
     *
     * @param maxTimestampAgeMsec
     *            the range of valid timestamps, in milliseconds into the past
     *            or future. So the total range of valid timestamps is twice
     *            this value, rounded to the nearest second.
     * @param maxVersion
     *            the maximum valid oauth_version
     */
    public SimpleOAuthValidator(long maxTimestampAgeMsec, double maxVersion) {
        this.maxTimestampAgeMsec = maxTimestampAgeMsec;
        this.maxVersion = maxVersion;
    }

    protected final double minVersion = 1.0;

    protected final double maxVersion;

    protected final long maxTimestampAgeMsec;

    private final Set<SimpleOAuthValidator.UsedNonce> usedNonces = new TreeSet<>();

    /**
     * Remove usedNonces with timestamps that are too old to be valid.
     */
    private Date removeOldNonces(long currentTimeMsec) {
        SimpleOAuthValidator.UsedNonce next = null;
        SimpleOAuthValidator.UsedNonce min = new SimpleOAuthValidator.UsedNonce((currentTimeMsec - maxTimestampAgeMsec + 500) / 1000L);
        synchronized (usedNonces) {
            // Because usedNonces is a TreeSet, its iterator produces
            // elements from oldest to newest (their natural order).
            for (Iterator<SimpleOAuthValidator.UsedNonce> iter = usedNonces.iterator(); iter.hasNext();) {
                SimpleOAuthValidator.UsedNonce used = iter.next();
                if (min.compareTo(used) <= 0) {
                    next = used;
                    break; // all the rest are also new enough
                }
                iter.remove(); // too old
            }
        }
        if (next == null)
            return null;
        return new Date((next.getTimestamp() * 1000L) + maxTimestampAgeMsec + 500);
    }

    public void validateMessage(OAuthMessage message, OAuthAccessor accessor) throws IOException, OAuthException, URISyntaxException {
        checkSingleParameters(message);
        validateVersion(message);
        validateTimestampAndNonce(message);
        validateSignature(message, accessor);
    }

    /** Throw an exception if any SINGLE_PARAMETERS occur repeatedly. */
    protected void checkSingleParameters(OAuthMessage message) throws IOException, OAuthProblemException {
        // Check for repeated oauth_ parameters:
        boolean repeated = false;
        Map<String, Collection<String>> nameToValues = new HashMap<>();
        for (Map.Entry<String, String> parameter : message.getParameters()) {
            String name = parameter.getKey();
            if (SINGLE_PARAMETERS.contains(name)) {
                Collection<String> values = nameToValues.get(name);
                if (values == null) {
                    values = new ArrayList<>();
                    nameToValues.put(name, values);
                }
                else {
                    repeated = true;
                }
                values.add(parameter.getValue());
            }
        }
        if (repeated) {
            Collection<OAuth.Parameter> rejected = new ArrayList<>();
            for (Map.Entry<String, Collection<String>> p : nameToValues.entrySet()) {
                String name = p.getKey();
                Collection<String> values = p.getValue();
                if (values.size() > 1) {
                    for (String value : values) {
                        rejected.add(new OAuth.Parameter(name, value));
                    }
                }
            }
            OAuthProblemException problem = new OAuthProblemException(OAuth.Problems.PARAMETER_REJECTED);
            problem.setParameter(OAuth.Problems.OAUTH_PARAMETERS_REJECTED, OAuth.formEncode(rejected));
            throw problem;
        }
    }

    protected void validateVersion(OAuthMessage message) throws OAuthProblemException {
        String versionString = message.getParameter(OAuth.OAUTH_VERSION);
        if (versionString != null) {
            double version = Double.parseDouble(versionString);
            if (version < minVersion || maxVersion < version) {
                OAuthProblemException problem = new OAuthProblemException(OAuth.Problems.VERSION_REJECTED);
                problem.setParameter(OAuth.Problems.OAUTH_ACCEPTABLE_VERSIONS, minVersion + "-" + maxVersion);
                throw problem;
            }
        }
    }

    /**
     * Throw an exception if the timestamp is out of range or the nonce has been
     * validated previously.
     */
    protected void validateTimestampAndNonce(OAuthMessage message) throws OAuthProblemException {
        message.requireParameters(OAuth.OAUTH_TIMESTAMP, OAuth.OAUTH_NONCE);
        long timestamp = Long.parseLong(message.getParameter(OAuth.OAUTH_TIMESTAMP));
        long now = System.currentTimeMillis();
        validateTimestamp(message, timestamp, now);
        validateNonce(message, timestamp, now);
    }

    /** Throw an exception if the timestamp [sec] is out of range. */
    protected void validateTimestamp(OAuthMessage message, long timestamp, long currentTimeMsec) throws OAuthProblemException {
        long min = (currentTimeMsec - maxTimestampAgeMsec + 500) / 1000L;
        long max = (currentTimeMsec + maxTimestampAgeMsec + 500) / 1000L;
        if (timestamp < min || max < timestamp) {
            OAuthProblemException problem = new OAuthProblemException(OAuth.Problems.TIMESTAMP_REFUSED);
            problem.setParameter(OAuth.Problems.OAUTH_ACCEPTABLE_TIMESTAMPS, min + "-" + max);
            throw problem;
        }
    }

    /**
     * Throw an exception if the nonce has been validated previously.
     *
     * @return the earliest point in time at which a call to releaseGarbage
     *         will actually release some garbage, or null to indicate there's
     *         nothing currently stored that will become garbage in future.
     */
    protected Date validateNonce(OAuthMessage message, long timestamp, long currentTimeMsec) throws OAuthProblemException {
        SimpleOAuthValidator.UsedNonce nonce = new SimpleOAuthValidator.UsedNonce(timestamp, message.getParameter(OAuth.OAUTH_NONCE), message.getConsumerKey(), message.getToken());
        /*
         * The OAuth standard requires the token to be omitted from the stored nonce. But I include it, to harmonize with a Consumer that generates nonces using several independent
         * computers, each with its own token.
         */
        boolean valid;
        synchronized (usedNonces) {
            valid = usedNonces.add(nonce);
        }
        if (!valid) {
            throw new OAuthProblemException(OAuth.Problems.NONCE_USED);
        }
        return removeOldNonces(currentTimeMsec);
    }

    protected void validateSignature(OAuthMessage message, OAuthAccessor accessor) throws IOException, OAuthException, URISyntaxException {
        message.requireParameters(OAuth.OAUTH_CONSUMER_KEY, OAuth.OAUTH_SIGNATURE_METHOD, OAuth.OAUTH_SIGNATURE);
        OAuthSignatureMethod.newSigner(message, accessor).validate(message);
    }

    /**
     * Selected parameters from an OAuth request, in a form suitable for
     * detecting duplicate requests. The implementation is optimized for the
     * comparison operations (compareTo, equals and hashCode).
     *
     * @author John Kristian
     */
    private static class UsedNonce implements Comparable<SimpleOAuthValidator.UsedNonce> {

        /**
         * Construct an object containing the given timestamp, nonce and other
         * parameters. The order of parameters is significant.
         */
        UsedNonce(long timestamp, String... nonceEtc) {
            StringBuilder key = new StringBuilder(String.format("%20d", timestamp));
            // The blank padding ensures that timestamps are compared as numbers.
            for (String etc : nonceEtc) {
                key.append("&").append(etc == null ? " " : OAuth.percentEncode(etc));
                // A null value is different from "" or any other String.
            }
            sortKey = key.toString();
        }

        private final String sortKey;

        long getTimestamp() {
            int end = sortKey.indexOf("&");
            if (end < 0)
                end = sortKey.length();
            return Long.parseLong(sortKey.substring(0, end).trim());
        }

        /**
         * Determine the relative order of <code>this</code> and
         * <code>that</code>, as specified by Comparable. The timestamp is most
         * significant; that is, if the timestamps are different, return 1 or
         * -1. If <code>this</code> contains only a timestamp (with no nonce
         * etc.), return -1 or 0. The treatment of the nonce etc. is murky,
         * although 0 is returned only if they're all equal.
         */
        public int compareTo(SimpleOAuthValidator.UsedNonce that) {
            return (that == null) ? 1 : sortKey.compareTo(that.sortKey);
        }

        @Override
        public int hashCode() {
            return sortKey.hashCode();
        }

        /**
         * Return true iff <code>this</code> and <code>that</code> contain equal
         * timestamps, nonce etc., in the same order.
         */
        @Override
        public boolean equals(Object that) {
            if (that == null)
                return false;
            if (that == this)
                return true;
            if (that.getClass() != getClass())
                return false;
            return sortKey.equals(((SimpleOAuthValidator.UsedNonce) that).sortKey);
        }

        @Override
        public String toString() {
            return sortKey;
        }
    }
}
