import { Component, Input, OnInit } from '@angular/core';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/entities/exam-session.model';
import { StudentExam } from 'app/entities/student-exam.model';

@Component({
    selector: 'jhi-suspicious-sessions',
    templateUrl: './suspicious-sessions.component.html',
    styleUrls: ['./suspicious-sessions.component.scss'],
})
export class SuspiciousSessionsComponent implements OnInit {
    @Input() suspiciousSessions: SuspiciousExamSessions;
    suspiciousFingerprint = false;
    suspiciousIpAddress = false;
    suspiciousUserAgent = false;
    ngOnInit(): void {
        this.suspiciousFingerprint = this.suspiciousSessions.examSessions.some((session) => session.suspiciousReasons.includes(SuspiciousSessionReason.SAME_BROWSER_FINGERPRINT));
        this.suspiciousIpAddress = this.suspiciousSessions.examSessions.some((session) => session.suspiciousReasons.includes(SuspiciousSessionReason.SAME_IP_ADDRESS));
        this.suspiciousUserAgent = this.suspiciousSessions.examSessions.some((session) => session.suspiciousReasons.includes(SuspiciousSessionReason.SAME_USER_AGENT));
    }

    getStudentExamLink(studentExam: StudentExam) {
        const studentExamId = studentExam.id;
        const courseId = studentExam.exam?.course?.id;
        const examId = studentExam.exam?.id;
        return `/course-management/${courseId}/exams/${examId}/student-exams/${studentExamId}`;
    }
}
