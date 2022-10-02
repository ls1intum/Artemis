package de.tum.in.www1.artemis.domain.plagiarism.text;

import java.io.File;

import javax.persistence.Column;
import javax.persistence.Entity;

import de.jplag.Token;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmissionElement;

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
     * @param token the JPlag Token to create the TextSubmissionElement from
     * @param plagiarismSubmission the PlagiarismSubmission the TextSubmissionElement belongs to
     * @param exercise the exercise to which the element belongs, either Text or Programming
     * @param submissionDirectory the directory to which all student submissions have been downloaded / stored
     * @return a new TextSubmissionElement instance
     */
    public static TextSubmissionElement fromJPlagToken(Token token, PlagiarismSubmission<TextSubmissionElement> plagiarismSubmission, Exercise exercise, File submissionDirectory) {
        TextSubmissionElement textSubmissionElement = new TextSubmissionElement();

        textSubmissionElement.setColumn(token.getColumn());
        textSubmissionElement.setLine(token.getLine());
        if (exercise instanceof ProgrammingExercise) {
            // Note: for text submissions 'file' must be null
            // Note: we want to get the relative path within the repository and not the absolute path
            var absolutePath = token.getFile();
            var filePath = submissionDirectory.getAbsoluteFile().toPath().relativize(absolutePath.getAbsoluteFile().toPath());
            // remove the first element, because it is the parent folder in which the whole repo was saved
            var fileStringWithinRepository = filePath.subpath(1, filePath.getNameCount()).toString();
            textSubmissionElement.setFile(fileStringWithinRepository);
        }
        textSubmissionElement.setLength(token.getLength());
        textSubmissionElement.setPlagiarismSubmission(plagiarismSubmission);

        return textSubmissionElement;
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
