package de.tum.in.www1.artemis.service.connectors.lti;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthException;
import oauth.signpost.http.HttpParameters;

public class IMSPOXRequest {

    public Document postDom = null;

    public Element bodyElement = null;

    public Element headerElement = null;

    public String postBody;

    public boolean valid = false;

    private String operation = null;

    public String errorMessage = null;

    public String getOperation() {
        return operation;
    }

    // Constructor for testing...
    public IMSPOXRequest(String bodyString) {
        postBody = bodyString;
        parsePostBody();
    }

    public static String getBodyHash(String postBody) throws GeneralSecurityException {
        MessageDigest md = MessageDigest.getInstance("SHA1");
        md.update(postBody.getBytes());
        byte[] output = Base64.encodeBase64(md.digest());
        return new String(output);
    }

    /**
     * parse the post body used in the request
     */
    public void parsePostBody() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            postDom = db.parse(new ByteArrayInputStream(postBody.getBytes()));
        }
        catch (Exception e) {
            errorMessage = "Could not parse XML: " + e.getMessage();
            return;
        }

        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            XPathExpression expr = xpath.compile("/imsx_POXEnvelopeRequest/imsx_POXBody/*");
            Object result = expr.evaluate(postDom, XPathConstants.NODESET);
            NodeList nodes = (NodeList) result;
            bodyElement = (Element) nodes.item(0);
            operation = bodyElement.getNodeName();

            expr = xpath.compile("/imsx_POXEnvelopeRequest/imsx_POXHeader/*");
            result = expr.evaluate(postDom, XPathConstants.NODESET);
            nodes = (NodeList) result;
            headerElement = (Element) nodes.item(0);
        }
        catch (javax.xml.xpath.XPathExpressionException e) {
            errorMessage = "Could not parse XPATH: " + e.getMessage();
            return;
        }
        catch (Exception e) {
            errorMessage = "Could not parse input XML: " + e.getMessage();
            return;
        }

        if (operation == null || bodyElement == null) {
            errorMessage = "Could not find operation";
            return;
        }
        valid = true;
    }

    /**
     * A template string for creating ReplaceResult messages.
     * Use like:
     * <pre>
     *     String.format(
     *       ReplaceResultMessageTemplate,
     *       messageId,
     *       sourcedId,
     *       resultScore,
     *       resultDataXml
     *     )
     * </pre>
     */
    public static final String ReplaceResultMessageTemplate = """
            <?xml version = "1.1" encoding = "UTF-8"?>
            <imsx_POXEnvelopeRequest xmlns="http://www.imsglobal.org/services/ltiv1p1/xsd/imsoms_v1p0">
            	<imsx_POXHeader>
            		<imsx_POXRequestHeaderInfo>
            			<imsx_version>V1.0</imsx_version>
            			<imsx_messageIdentifier>%s</imsx_messageIdentifier>
            		</imsx_POXRequestHeaderInfo>
            	</imsx_POXHeader>
            	<imsx_POXBody>
            		<replaceResultRequest>
            			<resultRecord>
            				<sourcedGUID>
            					<sourcedId>%s</sourcedId>
            				</sourcedGUID>
            				<result>
            					<resultScore>
            						<language>en</language>
            						<textString>%s</textString>
            					</resultScore>
            					%s
            				</result>
            			</resultRecord>
            		</replaceResultRequest>
            	</imsx_POXBody>
            </imsx_POXEnvelopeRequest>
            """;

    static final String resultDataText = "<resultData><text>%s</text></resultData>";

    static final String resultDataUrl = "<resultData><url>%s</url></resultData>";

    public static HttpPost buildReplaceResult(String url, String key, String secret, String sourcedid, String score, String resultData, Boolean isUrl)
            throws IOException, OAuthException, GeneralSecurityException {
        return buildReplaceResult(url, key, secret, sourcedid, score, resultData, isUrl, null);
    }

    /**
     * create the http post and by replacing the template elements in ReplaceResultMessageTemplate with the given values
     * @param url
     * @param key
     * @param secret
     * @param sourcedid
     * @param score
     * @param resultData
     * @param isUrl
     * @param messageId
     * @return a new http post
     * @throws OAuthException
     * @throws GeneralSecurityException
     */
    public static HttpPost buildReplaceResult(String url, String key, String secret, String sourcedid, String score, String resultData, Boolean isUrl, String messageId)
            throws OAuthException, GeneralSecurityException {
        String dataXml = "";
        if (resultData != null) {
            String format = isUrl ? resultDataUrl : resultDataText;
            dataXml = String.format(format, StringEscapeUtils.escapeXml11(resultData));
        }

        String messageIdentifier = StringUtils.isBlank(messageId) ? String.valueOf(new Date().getTime()) : messageId;
        String xml = String.format(ReplaceResultMessageTemplate, StringEscapeUtils.escapeXml11(messageIdentifier), StringEscapeUtils.escapeXml11(sourcedid),
                StringEscapeUtils.escapeXml11(score), dataXml);

        HttpParameters parameters = new HttpParameters();
        String hash = getBodyHash(xml);
        parameters.put("oauth_body_hash", URLEncoder.encode(hash, StandardCharsets.UTF_8));

        CommonsHttpOAuthConsumer signer = new CommonsHttpOAuthConsumer(key, secret);
        HttpPost request = new HttpPost(url);
        request.setHeader("Content-Type", "application/xml");
        request.setEntity(new StringEntity(xml, "UTF-8"));
        signer.setAdditionalParameters(parameters);
        signer.sign(request);
        return request;
    }
}
