package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.lecture.domain.Slide;

final class SlideVisibilitySnapshotHelper {

    private SlideVisibilitySnapshotHelper() {
    }

    static Map<Integer, ZonedDateTime> toSortedHiddenUntilBySlideNumber(List<Slide> slides) {
        var slideHiddenUntilBySlideNumber = new LinkedHashMap<Integer, ZonedDateTime>();
        slides.stream().sorted(Comparator.comparingInt(Slide::getSlideNumber)).forEach(slide -> slideHiddenUntilBySlideNumber.put(slide.getSlideNumber(), slide.getHidden()));
        return slideHiddenUntilBySlideNumber;
    }
}
