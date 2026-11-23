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

    /**
     * A name without angle brackets, control characters (Cc), format characters such as the right-to-left override (Cf) and line or paragraph separators (Zl, Zp): none of
     * them can end the name or the email in the commit header or change how it reads. The client uses the same character classes.
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("[^<>\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]+");

    /** A character that no part of an address may contain: an angle bracket, a control (Cc) or format (Cf) character, or any space or separator (Z). */
    private static final Pattern FORBIDDEN_ADDRESS_CHARACTER = Pattern.compile("[<>\\p{Cc}\\p{Cf}\\p{Z}]");

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
        if (strippedEmail.length() > MAX_EMAIL_LENGTH || !isValidEmail(strippedEmail)) {
            throw new BadRequestAlertException("The author email address is not valid.", "course", "invalidInitialCommitAuthorEmail");
        }
    }

    /**
     * Whether the text is one address: a local part, exactly one {@code @} and a domain that neither starts nor ends with a dot and has a dot inside,
     * without a forbidden character. It is checked by position, not by a pattern with a quantifier on each side of the dot, which backtracks heavily on a long text. The client
     * checks the same.
     */
    private static boolean isValidEmail(String address) {
        int at = address.indexOf('@');
        if (at <= 0 || at + 1 >= address.length() || address.charAt(at + 1) == '.' || address.endsWith(".") || at != address.lastIndexOf('@')
                || FORBIDDEN_ADDRESS_CHARACTER.matcher(address).find()) {
            return false;
        }
        int lastDomainIndex = address.length() - 1;
        for (int i = at + 2; i < lastDomainIndex; i++) {
            if (address.charAt(i) == '.') {
                return true;
            }
        }
        return false;
    }

    /**
     * The identity that authors the first commit according to this setting. For {@link InitialCommitAuthorMode#EXERCISE_CREATOR} this is the given creator, or null if there is
     * none. Null means that nothing is rewritten: the commit keeps the author it already has.
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
