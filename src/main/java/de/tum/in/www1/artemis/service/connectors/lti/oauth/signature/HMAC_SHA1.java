package de.tum.in.www1.artemis.service.connectors.lti.oauth.signature;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import de.tum.in.www1.artemis.service.connectors.lti.oauth.OAuth;
import de.tum.in.www1.artemis.service.connectors.lti.oauth.OAuthException;

/**
 * The HMAC-SHA1 signature method.
 *
 * @author John Kristian
 */
class HMAC_SHA1 extends OAuthSignatureMethod {

    @Override
    protected String getSignature(String baseString) throws OAuthException {
        try {
            return base64Encode(computeSignature(baseString));
        }
        catch (GeneralSecurityException | UnsupportedEncodingException e) {
            throw new OAuthException(e);
        }
    }

    @Override
    protected boolean isValid(String signature, String baseString) throws OAuthException {
        try {
            byte[] expected = computeSignature(baseString);
            byte[] actual = decodeBase64(signature);
            return equals(expected, actual);
        }
        catch (GeneralSecurityException | UnsupportedEncodingException e) {
            throw new OAuthException(e);
        }
    }

    private byte[] computeSignature(String baseString) throws GeneralSecurityException, UnsupportedEncodingException {
        SecretKey key;
        synchronized (this) {
            if (this.key == null) {
                String keyString = OAuth.percentEncode(getConsumerSecret()) + '&' + OAuth.percentEncode(getTokenSecret());
                byte[] keyBytes = keyString.getBytes(StandardCharsets.UTF_8);
                this.key = new SecretKeySpec(keyBytes, MAC_NAME);
            }
            key = this.key;
        }
        Mac mac = Mac.getInstance(MAC_NAME);
        mac.init(key);
        byte[] text = baseString.getBytes(StandardCharsets.UTF_8);
        return mac.doFinal(text);
    }

    private static final String MAC_NAME = "HmacSHA1";

    private SecretKey key = null;

    @Override
    public void setConsumerSecret(String consumerSecret) {
        synchronized (this) {
            key = null;
        }
        super.setConsumerSecret(consumerSecret);
    }

    @Override
    public void setTokenSecret(String tokenSecret) {
        synchronized (this) {
            key = null;
        }
        super.setTokenSecret(tokenSecret);
    }

}
