package de.tum.in.www1.artemis.domain.plagiarism.text;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmissionElement;

public class TextSubmissionElement extends PlagiarismSubmissionElement {

    private int column;

    private int line;

    private String file;

    private int length;

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
}
