package de.tum.cit.aet.artemis.course.dto;

import java.util.regex.Pattern;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.eclipse.jgit.lib.PersonIdent;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.domain.InitialCommitAuthorMode;

/**
 * Who authors the first commit of the template, solution and test repositories of new programming exercises of a course.
 * <p>
 * The name and the email address become the identity of a git commit, so they are checked strictly: a name that carries a line break, {@code <} or {@code >} could
 * otherwise forge further header lines or a second identity in the commit object.
 *
 * @param mode  who authors the commit
 * @param name  the author name, required for {@link InitialCommitAuthorMode#CUSTOM} and ignored otherwise
 * @param email the author email address, required for {@link InitialCommitAuthorMode#CUSTOM} and ignored otherwise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record InitialCommitAuthorDTO(@NotNull InitialCommitAuthorMode mode, @Nullable @Size(max = 100) String name, @Nullable @Size(max = 254) String email) {

    private static final int MAX_NAME_LENGTH = 100;

    private static final int MAX_EMAIL_LENGTH = 254;

    /** A name of printable characters without angle brackets: none of them can end the name or the email in the commit header. */
    private static final Pattern NAME_PATTERN = Pattern.compile("[^<>\\p{Cntrl}\\p{Zl}\\p{Zp}]+");

    /** One address without whitespace, angle brackets, control characters or a second {@code @}, with a dot in the domain. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[^\\s<>@\\p{Cntrl}]+@[^\\s<>@\\p{Cntrl}]+\\.[^\\s<>@\\p{Cntrl}]+");

    /**
     * Reads the setting of a course.
     *
     * @param configuration the configuration of the course
     * @return the setting
     */
    public static InitialCommitAuthorDTO of(CourseConfiguration configuration) {
        return new InitialCommitAuthorDTO(configuration.getInitialCommitAuthorMode(), configuration.getInitialCommitAuthorName(), configuration.getInitialCommitAuthorEmail());
    }

    /**
     * Rejects a custom identity that is blank, too long or that could inject git header data. The other modes carry no identity, so nothing is checked for them. This is
     * checked on the server in addition to the client because the update endpoint can be called without the form.
     *
     * @throws BadRequestAlertException if the identity is not valid
     */
    public void validate() {
        if (mode != InitialCommitAuthorMode.CUSTOM) {
            return;
        }
        String strippedName = name == null ? "" : name.strip();
        String strippedEmail = email == null ? "" : email.strip();
        if (strippedName.isEmpty() || strippedName.length() > MAX_NAME_LENGTH || !NAME_PATTERN.matcher(strippedName).matches()) {
            throw new BadRequestAlertException("The author name must not be blank, longer than " + MAX_NAME_LENGTH + " characters or contain line breaks, < or >.", "course",
                    "invalidInitialCommitAuthorName");
        }
        if (strippedEmail.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(strippedEmail).matches()) {
            throw new BadRequestAlertException("The author email address is not valid.", "course", "invalidInitialCommitAuthorEmail");
        }
    }

    /**
     * The identity that authors the first commit according to this setting.
     *
     * @param placeholder     the identity the server configures for commits it makes itself, used by {@link InitialCommitAuthorMode#PLACEHOLDER}
     * @param exerciseCreator the identity of the user who creates the exercise, or null where there is none, which leaves the author as it is
     * @return the identity, or null if the setting asks for the exercise creator and there is none
     */
    @Nullable
    public PersonIdent toIdentity(PersonIdent placeholder, @Nullable PersonIdent exerciseCreator) {
        // No switch over the enum: it would add a synthetic class to this package that the DTO naming rule rejects.
        if (mode == InitialCommitAuthorMode.PLACEHOLDER) {
            return placeholder;
        }
        // The setting is validated when it is stored; a custom one that lost its identity must not commit as nobody.
        if (mode == InitialCommitAuthorMode.CUSTOM && name != null && email != null) {
            return new PersonIdent(name.strip(), email.strip());
        }
        return exerciseCreator;
    }

    /**
     * The setting without surrounding whitespace in the identity, which is what gets stored.
     *
     * @return the stripped setting
     */
    public InitialCommitAuthorDTO stripped() {
        return new InitialCommitAuthorDTO(mode, name == null ? null : name.strip(), email == null ? null : email.strip());
    }
}
