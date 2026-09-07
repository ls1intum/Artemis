package de.tum.cit.aet.artemis.plagiarism.domain;

import java.io.File;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.jplag.Token;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@Entity
@Table(name = "plagiarism_submission_element")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PlagiarismSubmissionElement extends DomainObject {

    @ManyToOne
    private PlagiarismSubmission plagiarismSubmission;

    @Column(name = "file_column")
    private int column;

    private int line;

    private String file;

    /**
     * @deprecated JPlag reports this without counting line breaks, so it must not be used to derive the end position of
     *             a token. Kept because results computed before {@link #endLine} and {@link #endColumn} existed only
     *             have this value. Use the explicit end position for anything new.
     */
    @Deprecated
    private int length;

    /**
     * Where the token ends, as reported by JPlag. Null for results computed before these columns existed: the token
     * stream is not stored, so those rows cannot be backfilled and the client falls back to {@link #length}.
     */
    @Column(name = "end_line")
    private Integer endLine;

    @Column(name = "end_column")
    private Integer endColumn;

    /**
     * Create a new PlagiarismSubmissionElement instance from an existing JPlag Token
     *
     * @param token                the JPlag Token to create the PlagiarismSubmissionElement from
     * @param plagiarismSubmission the PlagiarismSubmission the PlagiarismSubmissionElement belongs to
     * @param exercise             the exercise to which the element belongs, either Text or Programming
     * @param submissionDirectory  the directory to which all student submissions have been downloaded / stored
     * @return a new PlagiarismSubmissionElement instance
     */
    public static PlagiarismSubmissionElement fromJPlagToken(Token token, PlagiarismSubmission plagiarismSubmission, Exercise exercise, File submissionDirectory) {
        PlagiarismSubmissionElement textSubmissionElement = new PlagiarismSubmissionElement();

        textSubmissionElement.setColumn(token.getStartColumn());
        textSubmissionElement.setLine(token.getStartLine());
        if (exercise instanceof ProgrammingExercise) {
            // Note: for text submissions 'file' must be null
            // Note: we want to get the relative path within the repository and not the absolute path
            final var fileStringWithinRepository = PlagiarismSubmissionElement.getString(token, submissionDirectory);
            textSubmissionElement.setFile(fileStringWithinRepository);
        }
        // getLength() is deprecated for removal, but it is still the only value results computed before the explicit
        // end position have, so it keeps being written until those results have aged out.
        @SuppressWarnings("removal")
        int tokenLength = token.getLength();
        textSubmissionElement.setLength(tokenLength);
        textSubmissionElement.setEndLine(token.getEndLine());
        textSubmissionElement.setEndColumn(token.getEndColumn());
        textSubmissionElement.setPlagiarismSubmission(plagiarismSubmission);

        return textSubmissionElement;
    }

    private static String getString(Token token, File submissionDirectory) {
        var submissionDirectoryAbsoluteFile = submissionDirectory.getAbsoluteFile();
        var tokenAbsoluteFile = token.getFile().getAbsoluteFile();
        var filePath = submissionDirectoryAbsoluteFile.toPath().relativize(tokenAbsoluteFile.toPath());
        // remove the first element, because it is the parent folder in which the whole repo was saved
        var fileStringWithinRepository = filePath.toString();
        if (filePath.getNameCount() > 1) {
            fileStringWithinRepository = filePath.subpath(1, filePath.getNameCount()).toString();
        }
        return fileStringWithinRepository;
    }

    public PlagiarismSubmission getPlagiarismSubmission() {
        return plagiarismSubmission;
    }

    public void setPlagiarismSubmission(PlagiarismSubmission plagiarismSubmission) {
        this.plagiarismSubmission = plagiarismSubmission;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    /**
     * @deprecated see the field: only correct for a token that stays on one line. Use {@link #getEndLine()} and
     *             {@link #getEndColumn()} instead, falling back to this for results that predate them.
     * @return the token length in characters, not counting line breaks
     */
    @Deprecated
    public int getLength() {
        return length;
    }

    public @Nullable Integer getEndLine() {
        return endLine;
    }

    public void setEndLine(@Nullable Integer endLine) {
        this.endLine = endLine;
    }

    public @Nullable Integer getEndColumn() {
        return endColumn;
    }

    public void setEndColumn(@Nullable Integer endColumn) {
        this.endColumn = endColumn;
    }

    public void setLength(int length) {
        this.length = length;
    }

    @Override
    public String toString() {
        return "PlagiarismSubmissionElement{" + "column=" + column + ", line=" + line + ", file='" + file + '\'' + ", length=" + length + '}';
    }
}
