package de.tum.in.www1.artemis.service.connectors.lti.oauth.signature;

import de.tum.in.www1.artemis.service.connectors.lti.oauth.OAuth;
import de.tum.in.www1.artemis.service.connectors.lti.oauth.OAuthException;

class PLAINTEXT extends OAuthSignatureMethod {

    @Override
    public String getSignature(String baseString) {
        return getSignature();
    }

    @Override
    protected boolean isValid(String signature, String baseString) throws OAuthException {
        return equals(getSignature(), signature);
    }

    private synchronized String getSignature() {
        if (signature == null) {
            signature = OAuth.percentEncode(getConsumerSecret()) + '&' + OAuth.percentEncode(getTokenSecret());
        }
        return signature;
    }

    private String signature = null;

    @Override
    public void setConsumerSecret(String consumerSecret) {
        synchronized (this) {
            signature = null;
        }
        super.setConsumerSecret(consumerSecret);
    }

    @Override
    public void setTokenSecret(String tokenSecret) {
        synchronized (this) {
            signature = null;
        }
        super.setTokenSecret(tokenSecret);
    }

}
