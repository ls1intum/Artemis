package de.tum.cit.aet.artemis.exam.dto;

import org.jspecify.annotations.Nullable;

/**
 * The identity images of one exam registration.
 *
 * <p>
 * Deleting a user has to remove those files after the database work, and the paths are all it needs from the
 * registration. Reading them as a projection keeps the deletion from loading whole {@code ExamUser} entities, of which
 * a long-serving account can have a great many.
 *
 * @param signingImagePath the stored signature image, if the user signed
 * @param studentImagePath the stored identity photo, if one was uploaded
 */
public record ExamUserImagePathsDTO(@Nullable String signingImagePath, @Nullable String studentImagePath) {
}
