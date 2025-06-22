package de.tum.cit.aet.artemis.plagiarism.domain;

import java.io.File;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.jplag.Token;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@Entity
@Table(name = "plagiarism_submission_element")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PlagiarismSubmissionElement extends DomainObject {

    @ManyToOne
    private PlagiarismSubmission plagiarismSubmission;

    @Column(name = "file_column")
    private int column;

    private int line;

    private String file;

    private int length;

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

        textSubmissionElement.setColumn(token.getColumn());
        textSubmissionElement.setLine(token.getLine());
        if (exercise instanceof ProgrammingExercise) {
            // Note: for text submissions 'file' must be null
            // Note: we want to get the relative path within the repository and not the absolute path
            final var fileStringWithinRepository = PlagiarismSubmissionElement.getString(token, submissionDirectory);
            textSubmissionElement.setFile(fileStringWithinRepository);
        }
        textSubmissionElement.setLength(token.getLength());
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

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    @Override
    public String toString() {
        return "PlagiarismSubmissionElement{" + "column=" + column + ", line=" + line + ", file='" + file + '\'' + ", length=" + length + '}';
    }
}
