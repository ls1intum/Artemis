import { Component, Input, OnInit } from '@angular/core';
import { SuspiciousExamSessions, SuspiciousSessionReason, toReadableString } from 'app/entities/exam-session.model';
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
    suspiciousReasonsString: string;

    ngOnInit(): void {
        this.suspiciousFingerprint = this.isSuspiciousFor(SuspiciousSessionReason.SAME_BROWSER_FINGERPRINT);
        this.suspiciousIpAddress = this.isSuspiciousFor(SuspiciousSessionReason.SAME_IP_ADDRESS);
        this.suspiciousUserAgent = this.isSuspiciousFor(SuspiciousSessionReason.SAME_USER_AGENT);
        this.suspiciousReasonsString =
            this.suspiciousSessions.examSessions
                ?.at(0)
                ?.suspiciousReasons?.map((reason) => toReadableString(reason))
                .join(', ') ?? '';
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
            case SuspiciousSessionReason.SAME_USER_AGENT:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.sameUserAgent';
        }
    }
}
