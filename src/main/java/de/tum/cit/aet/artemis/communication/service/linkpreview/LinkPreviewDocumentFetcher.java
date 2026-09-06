package de.tum.cit.aet.artemis.communication.service.linkpreview;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Retrieves HTML documents used to generate link previews.
 */
public final class LinkPreviewDocumentFetcher {

    private static final int MAX_REDIRECTS = 5;

    private static final int MAX_RESPONSE_SIZE = 2 * 1024 * 1024;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?:^|;)\\s*charset\\s*=\\s*[\\\"']?([^;\\\"'\\s]+)", Pattern.CASE_INSENSITIVE);

    private static final LinkPreviewUrlValidator URL_VALIDATOR = new LinkPreviewUrlValidator();

    private LinkPreviewDocumentFetcher() {
    }

    public static Document fetch(String url) throws IOException {
        return fetch(url, URL_VALIDATOR::validateAndResolve);
    }

    static Document fetch(String url, UrlResolver urlResolver) throws IOException {
        return fetch(url, urlResolver, REQUEST_TIMEOUT);
    }

    static Document fetch(String url, UrlResolver urlResolver, Duration requestTimeout) throws IOException {
        URI currentUri = createUri(url);
        LinkPreviewHttpClient.RequestDeadline requestDeadline = new LinkPreviewHttpClient.RequestDeadline(requestTimeout);

        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            LinkPreviewUrlValidator.ValidatedUrl validatedUrl = urlResolver.resolve(currentUri);
            LinkPreviewHttpClient.Response response = LinkPreviewHttpClient.get(validatedUrl, requestDeadline, MAX_RESPONSE_SIZE);
            if (!isRedirect(response.statusCode())) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Link preview request returned status " + response.statusCode());
                }
                return Jsoup.parse(new ByteArrayInputStream(response.body()), getCharsetName(response), validatedUrl.uri().toString());
            }
            if (redirectCount == MAX_REDIRECTS) {
                throw new IOException("Too many redirects while retrieving link preview");
            }
            String redirectLocation = response.firstHeader("Location");
            if (redirectLocation == null) {
                throw new IOException("Redirect response did not include a location");
            }
            currentUri = resolveRedirect(currentUri, redirectLocation);
        }

        throw new IOException("Could not retrieve link preview");
    }

    private static String getCharsetName(LinkPreviewHttpClient.Response response) {
        String contentType = response.firstHeader("Content-Type");
        if (contentType == null) {
            return null;
        }
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Charset.forName(matcher.group(1)).name();
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private static URI createUri(String url) throws IOException {
        try {
            return URI.create(url);
        }
        catch (IllegalArgumentException e) {
            throw new IOException("Invalid link preview URL", e);
        }
    }

    private static URI resolveRedirect(URI currentUri, String redirectLocation) throws IOException {
        try {
            return currentUri.resolve(redirectLocation);
        }
        catch (IllegalArgumentException e) {
            throw new IOException("Invalid link preview redirect", e);
        }
    }

    @FunctionalInterface
    interface UrlResolver {

        LinkPreviewUrlValidator.ValidatedUrl resolve(URI uri) throws IOException;
    }
}
