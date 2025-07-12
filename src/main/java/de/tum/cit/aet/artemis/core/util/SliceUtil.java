package de.tum.cit.aet.artemis.core.util;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Slice;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

public final class SliceUtil {

    private static final String HEADER_X_HAS_NEXT = "X-Has-Next";

    private static final String HEADER_LINK_FORMAT = "<{0}>; rel=\"{1}\"";

    private SliceUtil() {
    }

    /**
     * Generate pagination headers for a Spring Data {@link org.springframework.data.domain.Slice} object.
     *
     * @param uriBuilder The URI builder.
     * @param slice      The slice.
     * @param <T>        The type of object.
     * @return http header.
     */
    public static <T> HttpHeaders generateSliceHttpHeaders(UriComponentsBuilder uriBuilder, Slice<T> slice) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HEADER_X_HAS_NEXT, Boolean.toString(slice.hasNext()));
        headers.add(HttpHeaders.LINK, prepareLinkHeaders(uriBuilder, slice));
        return headers;
    }

    private static <T> String prepareLinkHeaders(UriComponentsBuilder uriBuilder, Slice<T> slice) {
        int pageNumber = slice.getNumber();
        int pageSize = slice.getSize();
        List<String> links = new ArrayList<>();
        if (slice.hasNext()) {
            links.add(prepareLink(uriBuilder, pageNumber + 1, pageSize, "next"));
        }
        if (pageNumber > 0) {
            links.add(prepareLink(uriBuilder, pageNumber - 1, pageSize, "prev"));
        }
        return String.join(",", links);
    }

    private static String prepareLink(UriComponentsBuilder uriBuilder, int pageNumber, int pageSize, String relType) {
        return MessageFormat.format(HEADER_LINK_FORMAT, preparePageUri(uriBuilder, pageNumber, pageSize), relType);
    }

    private static String preparePageUri(UriComponentsBuilder uriBuilder, int pageNumber, int pageSize) {
        return uriBuilder.replaceQueryParam("page", Integer.toString(pageNumber)).replaceQueryParam("size", Integer.toString(pageSize)).toUriString().replace(",", "%2C")
                .replace(";", "%3B");
    }
}
