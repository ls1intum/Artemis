package de.tum.in.www1.artemis.domain.exam;

public class StartExamResponse  {

    private StudentExam studentExam;

    private ExamSession examSession;

    public StartExamResponse() {
    }

    public StudentExam getStudentExam() {
        return studentExam;
    }

    public void setStudentExam(StudentExam studentExam) {
        this.studentExam = studentExam;
    }

    public ExamSession getExamSession() {
        return examSession;
    }

    public void setExamSession(ExamSession examSession) {
        this.examSession = examSession;
    }
}
