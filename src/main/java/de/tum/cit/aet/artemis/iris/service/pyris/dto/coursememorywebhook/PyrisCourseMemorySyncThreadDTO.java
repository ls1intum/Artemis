package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One thread of the nightly Course Memory sync: a thread that may have an entry in Pyris, as Artemis sees it now.
 *
 * @param postId   the thread's root post id
 * @param version  the thread's current Course Memory version; an entry written with an older version missed an update
 * @param eligible whether the thread may be stored right now (readable channel, Iris enabled for the course); {@code false}
 *                     is left out on the wire and Pyris reads a missing value as {@code false}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseMemorySyncThreadDTO(long postId, long version, boolean eligible) {
}
