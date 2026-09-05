package de.tum.cit.aet.artemis.account.repository.cleanup;

import org.jspecify.annotations.Nullable;

/**
 * The personal images held for one exam registration of an account that is being deleted.
 *
 * <p>
 * The rows go with the account, but the image files live outside the database and have to be scheduled for deletion
 * separately, so the deletion reads the two paths before removing the registrations.
 */
public interface ExamUserImagePaths {

    @Nullable
    String getSigningImagePath();

    @Nullable
    String getStudentImagePath();
}
