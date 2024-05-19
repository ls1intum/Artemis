package de.tum.in.www1.artemis.domain.iris.settings;

import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;

/**
 * An {@link IrisSubSettings} implementation for chat settings.
 * Chat settings notably provide settings for the rate limit.
 * Chat settings provide a single {@link IrisTemplate} for the chat messages.
 */
@Entity
@DiscriminatorValue("CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisChatSubSettings extends IrisSubSettings {

    @Nullable
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private IrisTemplate template;

    @Nullable
    @Column(name = "rate_limit")
    private Integer rateLimit;

    @Nullable
    @Column(name = "rate_limit_timeframe_hours")
    private Integer rateLimitTimeframeHours;

    @Nullable
    @Column(name = "lecture_chat")
    private Boolean lectureChat;

    @Nullable
    public Boolean getLectureChat() {
        return lectureChat;
    }

    public void setLectureChat(@Nullable Boolean lectureChat) {
        this.lectureChat = lectureChat;
    }

    @Nullable
    public IrisTemplate getTemplate() {
        return template;
    }

    public void setTemplate(@Nullable IrisTemplate template) {
        this.template = template;
    }

    @Nullable
    public Integer getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(@Nullable Integer rateLimit) {
        this.rateLimit = rateLimit;
    }

    @Nullable
    public Integer getRateLimitTimeframeHours() {
        return rateLimitTimeframeHours;
    }

    public void setRateLimitTimeframeHours(@Nullable Integer rateLimitTimeframeHours) {
        this.rateLimitTimeframeHours = rateLimitTimeframeHours;
    }
}
