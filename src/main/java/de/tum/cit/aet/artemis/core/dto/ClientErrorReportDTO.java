package de.tum.cit.aet.artemis.core.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.ClientErrorSeverity;

/**
 * An error that the client reports to the server, which writes it to the log.
 * <p>
 * The endpoint is open to clients that are not authenticated, so every field is bounded. The server does not trust the limits
 * of the client and shortens and cleans every value again before it logs it.
 *
 * @param severity      how serious the error is, which decides the log level of the entry
 * @param message       what went wrong
 * @param stack         the stack trace of the error, if there is one
 * @param url           the page the error happened on, the server drops the query and the fragment
 * @param clientVersion the version of the client that reports the error
 * @param extra         additional information that helps to understand the error, such as the identifiers of the affected entities
 * @param tags          short labels with few distinct values to group related errors, such as the affected feature
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ClientErrorReportDTO(@NotNull ClientErrorSeverity severity, @NotBlank @Size(max = ClientErrorReportDTO.MAX_MESSAGE_LENGTH) String message,
        @Size(max = ClientErrorReportDTO.MAX_STACK_LENGTH) String stack, @Size(max = ClientErrorReportDTO.MAX_URL_LENGTH) String url,
        @Size(max = ClientErrorReportDTO.MAX_VERSION_LENGTH) String clientVersion,
        @Size(max = ClientErrorReportDTO.MAX_ENTRIES) Map<@Size(max = ClientErrorReportDTO.MAX_KEY_LENGTH) String, @Size(max = ClientErrorReportDTO.MAX_VALUE_LENGTH) String> extra,
        @Size(max = ClientErrorReportDTO.MAX_ENTRIES) Map<@Size(max = ClientErrorReportDTO.MAX_KEY_LENGTH) String, @Size(max = ClientErrorReportDTO.MAX_VALUE_LENGTH) String> tags) {

    public static final int MAX_MESSAGE_LENGTH = 1_000;

    public static final int MAX_STACK_LENGTH = 8_000;

    public static final int MAX_URL_LENGTH = 500;

    public static final int MAX_VERSION_LENGTH = 50;

    /** The most entries of {@link #extra} and of {@link #tags}. */
    public static final int MAX_ENTRIES = 10;

    public static final int MAX_KEY_LENGTH = 50;

    public static final int MAX_VALUE_LENGTH = 500;
}
