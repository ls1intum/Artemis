package de.tum.cit.aet.artemis.communication.service.linkpreview;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?:^|;)\\s*charset\\s*=\\s*[\\\"']?([^;\\\"'\\s]+)", Pattern.CASE_INSENSITIVE);

    private static final LinkPreviewUrlValidator URL_VALIDATOR = new LinkPreviewUrlValidator();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    private LinkPreviewDocumentFetcher() {
    }

    public static Document fetch(String url) throws IOException {
        return fetch(url, URL_VALIDATOR::validateAndResolve);
    }

    static Document fetch(String url, UrlResolver urlResolver) throws IOException {
        URI currentUri = createUri(url);

        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            LinkPreviewUrlValidator.ValidatedUrl validatedUrl = urlResolver.resolve(currentUri);
            FetchResponse response = fetch(validatedUrl);
            if (response.redirectLocation() == null) {
                return Jsoup.parse(new ByteArrayInputStream(response.body()), response.charsetName(), validatedUrl.uri().toString());
            }
            if (redirectCount == MAX_REDIRECTS) {
                throw new IOException("Too many redirects while retrieving link preview");
            }
            currentUri = resolveRedirect(currentUri, response.redirectLocation());
        }

        throw new IOException("Could not retrieve link preview");
    }

    private static FetchResponse fetch(LinkPreviewUrlValidator.ValidatedUrl validatedUrl) throws IOException {
        var request = HttpRequest.newBuilder(validatedUrl.uri()).timeout(Duration.ofSeconds(10)).header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", "Artemis Link Preview").GET().build();
        HttpResponse<InputStream> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Link preview request was interrupted", e);
        }

        try (InputStream responseBody = response.body()) {
            int statusCode = response.statusCode();
            if (isRedirect(statusCode)) {
                String location = response.headers().firstValue("Location").orElseThrow(() -> new IOException("Redirect response did not include a location"));
                return new FetchResponse(location, null, null);
            }
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("Link preview request returned status " + statusCode);
            }

            byte[] body = responseBody.readNBytes(MAX_RESPONSE_SIZE + 1);
            if (body.length > MAX_RESPONSE_SIZE) {
                throw new IOException("Link preview response exceeded the maximum size");
            }
            return new FetchResponse(null, body, getCharsetName(response.headers()));
        }
    }

    private static String getCharsetName(java.net.http.HttpHeaders headers) {
        String contentType = headers.firstValue("Content-Type").orElse("");
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

    private record FetchResponse(String redirectLocation, byte[] body, String charsetName) {
    }
}
