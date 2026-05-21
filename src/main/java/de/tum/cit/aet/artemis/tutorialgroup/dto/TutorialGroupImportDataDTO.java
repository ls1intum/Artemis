package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupImportErrors;

/**
 * DTO used for client-server communication in the import of tutorial groups and student registrations from csv files
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupImportDataDTO(@Nullable String title, @Nullable StudentDTO student, @Nullable Boolean importSuccessful, @Nullable TutorialGroupImportErrors error,
        @Nullable String campus, @Nullable Integer capacity, @Nullable String language, @Nullable String additionalInformation, @Nullable Boolean isOnline) {

    public TutorialGroupImportDataDTO withImportResult(boolean importSuccessful, TutorialGroupImportErrors error) {
        return new TutorialGroupImportDataDTO(title(), student(), importSuccessful, error, campus(), capacity(), language(), additionalInformation(), isOnline());
    }

    public TutorialGroupImportDataDTO(@Nullable String title, @Nullable StudentDTO student, @Nullable String campus, @Nullable Integer capacity, @Nullable String language,
            @Nullable String additionalInformation, @Nullable Boolean isOnline) {
        this(title, student, null, null, campus, capacity, language, additionalInformation, isOnline);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        TutorialGroupImportDataDTO that = (TutorialGroupImportDataDTO) object;

        if (!Objects.equals(title, that.title)) {
            return false;
        }
        return Objects.equals(student, that.student);
    }

    @Override
    public int hashCode() {
        int result = title != null ? title.hashCode() : 0;
        result = 31 * result + (student != null ? student.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TutorialGroupRegistrationImportDTO{" + "title='" + title + '\'' + ", student=" + student + ", importSuccessful=" + importSuccessful + ", error=" + error
                + ", campus=" + campus + ", capacity=" + capacity + ", language=" + language + ", additionalInformation=" + additionalInformation + ", isOnline=" + isOnline + '}';
    }
}
