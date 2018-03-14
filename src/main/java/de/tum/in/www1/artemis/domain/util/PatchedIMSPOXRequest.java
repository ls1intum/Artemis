package de.tum.in.www1.artemis.domain.util;

import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthException;
import oauth.signpost.http.HttpParameters;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.imsglobal.pox.IMSPOXRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;

/**
 * Patch for IMSPOXRequest until they fixed the problem.
 * Issue: https://github.com/IMSGlobal/basiclti-util-java/issues/27
 *
 * Created by Josias Montag on 02.10.16.
 */
public class PatchedIMSPOXRequest extends IMSPOXRequest {

    public PatchedIMSPOXRequest(String oauth_consumer_key, String oauth_secret, HttpServletRequest request)
    {
        super(oauth_consumer_key, oauth_secret, request);
    }

    // Constructor for delayed validation
    public PatchedIMSPOXRequest(HttpServletRequest request)
    {
        super(request);
    }

    // Constructor for testing...
    public PatchedIMSPOXRequest(String bodyString)
    {
        super(bodyString);
    }



    public static HttpPost buildReplaceResult(String url, String key, String secret, String sourcedid, String score, String resultData, Boolean isUrl) throws IOException, OAuthException, GeneralSecurityException {
        String dataXml = "";
        if (resultData != null) {
            String format = isUrl ? resultDataUrl : resultDataText;
            dataXml = String.format(format, StringEscapeUtils.escapeXml(resultData));
        }
        String xml = String.format(patchedReplaceResultMessage, StringEscapeUtils.escapeXml(sourcedid),
            StringEscapeUtils.escapeXml(score), dataXml);

        HttpParameters parameters = new HttpParameters();
        String hash = getBodyHash(xml);
        parameters.put("oauth_body_hash", URLEncoder.encode(hash, "UTF-8"));

        CommonsHttpOAuthConsumer signer = new CommonsHttpOAuthConsumer(key, secret);
        HttpPost request = new HttpPost(url);
        request.setHeader("Content-Type", "application/xml");
        request.setEntity(new StringEntity(xml, "UTF-8"));
        signer.setAdditionalParameters(parameters);
        signer.sign(request);
        return request;
    }

    static final String resultDataText = "<resultData><text>%s</text></resultData>";

    static final String resultDataUrl = "<resultData><url>%s</url></resultData>";



    public static final String patchedReplaceResultMessage =
        "<?xml version = \"1.0\" encoding = \"UTF-8\"?>"+
            "<imsx_POXEnvelopeRequest xmlns=\"http://www.imsglobal.org/services/ltiv1p1/xsd/imsoms_v1p0\">"+
            "	<imsx_POXHeader>"+
            "		<imsx_POXRequestHeaderInfo>"+
            "			<imsx_version>V1.0</imsx_version>"+
            "		</imsx_POXRequestHeaderInfo>"+
            "       <imsx_messageIdentifier>999999123</imsx_messageIdentifier>" +
            "	</imsx_POXHeader>"+
            "	<imsx_POXBody>"+
            "		<replaceResultRequest>"+
            "			<resultRecord>"+
            "				<sourcedGUID>"+
            "					<sourcedId>%s</sourcedId>"+
            "				</sourcedGUID>"+
            "				<result>"+
            "					<resultScore>"+
            "						<textString>%s</textString>"+
            "					</resultScore>"+
            "					%s"+
            "				</result>"+
            "			</resultRecord>"+
            "		</replaceResultRequest>"+
            "	</imsx_POXBody>"+
            "</imsx_POXEnvelopeRequest>";
}


