import { Component, Input } from '@angular/core';
import { SuspiciousExamSessions } from 'app/entities/exam-session.model';
import { StudentExam } from 'app/entities/student-exam.model';

@Component({
    selector: 'jhi-suspicious-sessions',
    templateUrl: './suspicious-sessions.component.html',
})
export class SuspiciousSessionsComponent {
    @Input() suspiciousSessions: SuspiciousExamSessions;

    getStudentExamLink(studentExam: StudentExam) {
        const studentExamId = studentExam.id;
        const courseId = studentExam.exam?.course?.id;
        const examId = studentExam.exam?.id;
        return `/course-management/${courseId}/exams/${examId}/student-exams/${studentExamId}`;
    }
}
