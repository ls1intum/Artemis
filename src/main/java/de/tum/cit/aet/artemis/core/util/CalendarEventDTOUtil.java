package de.tum.cit.aet.artemis.core.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import de.tum.cit.aet.artemis.core.domain.Language;

public class CalendarEventDTOUtil {

    private record EventDescriptorKey(Language language, CalendarEventRelatedEntity relatedEntity, CalendarEventSemantics calendarEventSemantics) {
    }

    private static final String DE_EXERCISE_RELEASE_DATE = "Veröffentlichung";

    private static final String EN_EXERCISE_RELEASE_DATE = "Release";

    private static final String DE_EXERCISE_START_DATE = "Start";

    private static final String EN_EXERCISE_START_DATE = "Start";

    private static final String DE_EXERCISE_DUE_DATE = "Abgabefrist";

    private static final String EN_EXERCISE_DUE_DATE = "Due";

    private static final String DE_EXERCISE_ASSESSMENT_DUE_DATE = "Korrekturfrist";

    private static final String EN_EXERCISE_ASSESSMENT_DUE_DATE = "Assessment Due";

    private static final Map<EventDescriptorKey, String> eventDescriptorMap = buildMap();

    private static Map<EventDescriptorKey, String> buildMap() {
        Map<EventDescriptorKey, String> map = new HashMap<>();

        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.LECTURE, CalendarEventSemantics.START_DATE), "Start");
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.LECTURE, CalendarEventSemantics.START_DATE), "Start");
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.LECTURE, CalendarEventSemantics.END_DATE), "End");
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.LECTURE, CalendarEventSemantics.END_DATE), "Ende");

        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.PUBLISH_RESULTS_DATE), "Results Release");
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.PUBLISH_RESULTS_DATE), "Veröffentlichung Ergebnisse");
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.STUDENT_REVIEW_START_DATE), "Review Start");
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.STUDENT_REVIEW_START_DATE), "Einsicht Start");
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.STUDENT_REVIEW_END_DATE), "Review End");
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.STUDENT_REVIEW_END_DATE), "Review Ende");

        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.RELEASE_DATE), EN_EXERCISE_RELEASE_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.RELEASE_DATE), DE_EXERCISE_RELEASE_DATE);
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.DUE_DATE), EN_EXERCISE_DUE_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.DUE_DATE), DE_EXERCISE_DUE_DATE);

        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.TEXT_EXERCISE, CalendarEventSemantics.RELEASE_DATE), EN_EXERCISE_RELEASE_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.TEXT_EXERCISE, CalendarEventSemantics.RELEASE_DATE), DE_EXERCISE_RELEASE_DATE);
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.TEXT_EXERCISE, CalendarEventSemantics.START_DATE), EN_EXERCISE_START_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.TEXT_EXERCISE, CalendarEventSemantics.START_DATE), DE_EXERCISE_START_DATE);
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.TEXT_EXERCISE, CalendarEventSemantics.DUE_DATE), EN_EXERCISE_DUE_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.TEXT_EXERCISE, CalendarEventSemantics.DUE_DATE), DE_EXERCISE_DUE_DATE);
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.TEXT_EXERCISE, CalendarEventSemantics.ASSESSMENT_DUE_DATE), EN_EXERCISE_ASSESSMENT_DUE_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.TEXT_EXERCISE, CalendarEventSemantics.ASSESSMENT_DUE_DATE), DE_EXERCISE_ASSESSMENT_DUE_DATE);

        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.MODELING_EXERCISE, CalendarEventSemantics.RELEASE_DATE), EN_EXERCISE_RELEASE_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.MODELING_EXERCISE, CalendarEventSemantics.RELEASE_DATE), DE_EXERCISE_RELEASE_DATE);
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.MODELING_EXERCISE, CalendarEventSemantics.START_DATE), EN_EXERCISE_START_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.MODELING_EXERCISE, CalendarEventSemantics.START_DATE), DE_EXERCISE_START_DATE);
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.MODELING_EXERCISE, CalendarEventSemantics.DUE_DATE), EN_EXERCISE_DUE_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.MODELING_EXERCISE, CalendarEventSemantics.DUE_DATE), DE_EXERCISE_DUE_DATE);
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.MODELING_EXERCISE, CalendarEventSemantics.ASSESSMENT_DUE_DATE),
                EN_EXERCISE_ASSESSMENT_DUE_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.MODELING_EXERCISE, CalendarEventSemantics.ASSESSMENT_DUE_DATE), DE_EXERCISE_ASSESSMENT_DUE_DATE);

        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.PROGRAMMING_EXERCISE, CalendarEventSemantics.RELEASE_DATE), EN_EXERCISE_RELEASE_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.PROGRAMMING_EXERCISE, CalendarEventSemantics.RELEASE_DATE), DE_EXERCISE_RELEASE_DATE);
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.PROGRAMMING_EXERCISE, CalendarEventSemantics.START_DATE), EN_EXERCISE_START_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.PROGRAMMING_EXERCISE, CalendarEventSemantics.START_DATE), DE_EXERCISE_START_DATE);
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.PROGRAMMING_EXERCISE, CalendarEventSemantics.DUE_DATE), EN_EXERCISE_DUE_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.PROGRAMMING_EXERCISE, CalendarEventSemantics.DUE_DATE), DE_EXERCISE_DUE_DATE);
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.PROGRAMMING_EXERCISE, CalendarEventSemantics.ASSESSMENT_DUE_DATE),
                EN_EXERCISE_ASSESSMENT_DUE_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.PROGRAMMING_EXERCISE, CalendarEventSemantics.ASSESSMENT_DUE_DATE),
                DE_EXERCISE_ASSESSMENT_DUE_DATE);

        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE, CalendarEventSemantics.RELEASE_DATE), EN_EXERCISE_RELEASE_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE, CalendarEventSemantics.RELEASE_DATE), DE_EXERCISE_RELEASE_DATE);
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE, CalendarEventSemantics.START_DATE), EN_EXERCISE_START_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE, CalendarEventSemantics.START_DATE), DE_EXERCISE_START_DATE);
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE, CalendarEventSemantics.DUE_DATE), EN_EXERCISE_DUE_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE, CalendarEventSemantics.DUE_DATE), DE_EXERCISE_DUE_DATE);
        map.put(new EventDescriptorKey(Language.ENGLISH, CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE, CalendarEventSemantics.ASSESSMENT_DUE_DATE),
                EN_EXERCISE_ASSESSMENT_DUE_DATE);
        map.put(new EventDescriptorKey(Language.GERMAN, CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE, CalendarEventSemantics.ASSESSMENT_DUE_DATE),
                DE_EXERCISE_ASSESSMENT_DUE_DATE);

        return Map.copyOf(map);
    }

    public static Optional<String> getCalendarEventDescriptor(Language language, CalendarEventRelatedEntity relatedEntity, CalendarEventSemantics semantics) {
        return Optional.ofNullable(eventDescriptorMap.get(new EventDescriptorKey(language, relatedEntity, semantics)));
    }
}
