package de.tum.cit.aet.artemis.core.web.util;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Inlined replacement for {@code tech.jhipster.web.util.PaginationUtil}.
 * <p>
 * Utility methods for generating pagination HTTP headers (Link and X-Total-Count).
 */
public final class PaginationUtil {

    private static final String HEADER_X_TOTAL_COUNT = "X-Total-Count";

    private PaginationUtil() {
    }

    /**
     * Generates HTTP headers for paginated responses, including Link and X-Total-Count headers.
     *
     * @param <T>        the type of the page content
     * @param uriBuilder the URI builder used to construct pagination links
     * @param page       the page result containing pagination metadata
     * @return {@link HttpHeaders} with pagination Link and total count headers
     */
    public static <T> HttpHeaders generatePaginationHttpHeaders(UriComponentsBuilder uriBuilder, Page<T> page) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HEADER_X_TOTAL_COUNT, Long.toString(page.getTotalElements()));

        int pageNumber = page.getNumber();
        int pageSize = page.getSize();
        int totalPages = page.getTotalPages();
        StringBuilder link = new StringBuilder();

        if (pageNumber < totalPages - 1) {
            link.append(prepareLink(uriBuilder, pageNumber + 1, pageSize, "next"));
        }
        if (pageNumber > 0) {
            if (!link.isEmpty()) {
                link.append(",");
            }
            link.append(prepareLink(uriBuilder, pageNumber - 1, pageSize, "prev"));
        }
        if (!link.isEmpty()) {
            link.append(",");
        }
        // Guard against empty results where totalPages is 0
        int lastPage = Math.max(0, totalPages - 1);
        link.append(prepareLink(uriBuilder, lastPage, pageSize, "last"));
        link.append(",");
        link.append(prepareLink(uriBuilder, 0, pageSize, "first"));

        headers.add(HttpHeaders.LINK, link.toString());
        return headers;
    }

    private static String prepareLink(UriComponentsBuilder uriBuilder, int pageNumber, int pageSize, String relType) {
        // Clone the builder to avoid mutating the caller's instance
        return "<" + uriBuilder.cloneBuilder().replaceQueryParam("page", pageNumber).replaceQueryParam("size", pageSize).toUriString() + ">; rel=\"" + relType + "\"";
    }
}
