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
    ngOnInit(): void {
        this.suspiciousFingerprint = this.isSuspiciousFor(SuspiciousSessionReason.SAME_BROWSER_FINGERPRINT);
        this.suspiciousIpAddress = this.isSuspiciousFor(SuspiciousSessionReason.SAME_IP_ADDRESS);
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

    mapEnumToTranslationString(reason: SuspiciousSessionReason) {
        switch (reason) {
            case SuspiciousSessionReason.SAME_IP_ADDRESS:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.sameIpAddress';
            case SuspiciousSessionReason.SAME_BROWSER_FINGERPRINT:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.sameBrowserFingerprint';
        }
    }
}
