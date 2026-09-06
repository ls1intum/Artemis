package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

/**
 * Origin of a Course Memory ingestion request. Sent to Pyris as the {@code source} field and used by
 * the ingestion pipeline to decide how the verified answer is derived and labelled.
 *
 * The distinction the values draw is the <em>trust tier</em> — who signed off on the answer — not who
 * typed it. A tutor marking a student's answer as resolving endorses it just as much as writing it.
 *
 * <ul>
 * <li>{@link #IRIS_AUTO} – a tutor approved an Iris-generated draft unchanged (Trigger A), or endorsed an
 * automatically published Iris answer by marking it resolving (Trigger B). The approved text is passed
 * verbatim via {@code existingAnswer}: the sign-off is on that exact wording, and Pyris rejects the payload
 * without it rather than store an extractor's paraphrase as tutor-approved.</li>
 * <li>{@link #TUTOR_WRITTEN} – a tutor endorsed a human-written answer, no Iris draft involved (Trigger A,
 * or Trigger B when a tutor marks the answer resolving).</li>
 * <li>{@link #IRIS_CORRECTED} – a tutor edited an Iris draft before approving it (Trigger A); the
 * edited text is passed verbatim via {@code existingAnswer}, required like for {@link #IRIS_AUTO}.</li>
 * <li>{@link #THREAD_RESOLVED} – a thread was resolved without a tutor endorsing the answer, e.g. a
 * student marking a reply as resolving (Trigger B). Pyris labels these as not tutor-verified.</li>
 * </ul>
 */
public enum PyrisCourseMemorySource {
    IRIS_AUTO, TUTOR_WRITTEN, IRIS_CORRECTED, THREAD_RESOLVED
}
