import { Component, Input, OnInit } from '@angular/core';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/entities/exam/exam-session.model';
import { StudentExam } from 'app/entities/student-exam.model';

@Component({
    // this is intended and an attribute selector because otherwise the rendered table breaks
    // eslint-disable-next-line @angular-eslint/component-selector
    selector: '[jhi-suspicious-sessions]',
    templateUrl: './suspicious-sessions.component.html',
    styleUrls: ['./suspicious-sessions.component.scss'],
})
export class SuspiciousSessionsComponent implements OnInit {
    @Input() suspiciousSessions: SuspiciousExamSessions;
    suspiciousFingerprint = false;
    suspiciousIpAddress = false;
    ngOnInit(): void {
        this.suspiciousFingerprint =
            this.isSuspiciousFor(SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT) ||
            this.isSuspiciousFor(SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS);
        this.suspiciousIpAddress =
            this.isSuspiciousFor(SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS) ||
            this.isSuspiciousFor(SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES) ||
            this.isSuspiciousFor(SuspiciousSessionReason.IP_ADDRESS_OUTSIDE_OF_RANGE);
    }

    getStudentExamLink(studentExam: StudentExam) {
        const studentExamId = studentExam.id;
        const courseId = studentExam.exam?.course?.id;
        const examId = studentExam.exam?.id;
        return `/course-management/${courseId}/exams/${examId}/student-exams/${studentExamId}`;
    }

    private isSuspiciousFor(reason: SuspiciousSessionReason) {
        return this.suspiciousSessions.examSessions.some((session) => session.suspiciousReasons.includes(reason));
    }
}
