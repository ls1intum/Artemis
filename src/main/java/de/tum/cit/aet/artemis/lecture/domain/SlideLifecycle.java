package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;

/**
 * The lifecycle events of a slide.
 */
public enum SlideLifecycle {

    /**
     * The date when the slide becomes visible.
     */
    UNHIDE {

        @Override
        public ZonedDateTime getDateFromSlide(Slide slide) {
            return slide.getHidden();
        }
    };

    /**
     * Returns the date when this lifecycle event happens for the specified slide.
     *
     * @param slide The slide
     * @return The date when this lifecycle event happens
     */
    public abstract ZonedDateTime getDateFromSlide(Slide slide);
}
