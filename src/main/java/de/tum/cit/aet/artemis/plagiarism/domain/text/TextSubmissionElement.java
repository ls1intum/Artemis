package de.tum.cit.aet.artemis.plagiarism.domain.text;

import java.io.File;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import de.jplag.Token;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmissionElement;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@Entity
// TODO: use @DiscriminatorValue(value = "T") or even better use integers (because they use less space in the database)
public class TextSubmissionElement extends PlagiarismSubmissionElement {

    @Column(name = "file_column")
    private int column;

    private int line;

    private String file;

    // TODO: remove this column, we don't really need it
    @Column(name = "token_type")
    private int type;

    private int length;

    /**
     * Create a new TextSubmissionElement instance from an existing JPlag Token
     *
     * @param token                the JPlag Token to create the TextSubmissionElement from
     * @param plagiarismSubmission the PlagiarismSubmission the TextSubmissionElement belongs to
     * @param exercise             the exercise to which the element belongs, either Text or Programming
     * @param submissionDirectory  the directory to which all student submissions have been downloaded / stored
     * @return a new TextSubmissionElement instance
     */
    public static TextSubmissionElement fromJPlagToken(Token token, PlagiarismSubmission<TextSubmissionElement> plagiarismSubmission, Exercise exercise, File submissionDirectory) {
        TextSubmissionElement textSubmissionElement = new TextSubmissionElement();

        textSubmissionElement.setColumn(token.getColumn());
        textSubmissionElement.setLine(token.getLine());
        if (exercise instanceof ProgrammingExercise) {
            // Note: for text submissions 'file' must be null
            // Note: we want to get the relative path within the repository and not the absolute path
            final var fileStringWithinRepository = getString(token, submissionDirectory);
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

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    @Override
    public String toString() {
        return "TextSubmissionElement{" + "column=" + column + ", line=" + line + ", file='" + file + '\'' + ", type=" + type + ", length=" + length + '}';
    }
}
