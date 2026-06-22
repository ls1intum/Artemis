package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

/**
 * Origin of a Course Memory ingestion request. Sent to Pyris as the {@code source} field and used by
 * the ingestion pipeline to decide how the verified answer is derived and labelled.
 *
 * <ul>
 * <li>{@link #IRIS_AUTO} – a tutor approved an Iris-generated draft unchanged (Trigger A).</li>
 * <li>{@link #TUTOR_WRITTEN} – a tutor wrote their own answer, no Iris draft involved (Trigger A).</li>
 * <li>{@link #IRIS_CORRECTED} – a tutor edited an Iris draft before approving it (Trigger A); the
 * edited text is passed verbatim via {@code existingAnswer}.</li>
 * <li>{@link #THREAD_RESOLVED} – a thread was marked resolved without going through verification
 * (Trigger B).</li>
 * </ul>
 */
public enum PyrisCourseMemorySource {
    IRIS_AUTO, TUTOR_WRITTEN, IRIS_CORRECTED, THREAD_RESOLVED
}
