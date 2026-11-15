package de.tum.cit.aet.artemis.communication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.core.config.StrictLongDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The request to post an announcement to a course.
 *
 * @param content   the text of the announcement, in Markdown
 * @param title     an optional headline
 * @param channelId the announcement channel to post to; the course's default announcement channel if omitted
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateAnnouncementDTO(@Schema(description = "The text of the announcement, in Markdown.") @NotBlank String content,
        @Schema(description = "An optional headline.") @Nullable @Size(max = 200) String title,
        @Schema(description = "The announcement channel to post to. The course's default announcement channel if omitted.") @Nullable @JsonDeserialize(using = StrictLongDeserializer.class) Long channelId) {
}
