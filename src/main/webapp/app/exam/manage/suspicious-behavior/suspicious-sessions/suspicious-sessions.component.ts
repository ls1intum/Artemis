import { CommonModule } from '@angular/common';
import { Component, OnInit, input, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import type { SuspiciousExamSessions } from 'app/openapi/model/suspicious-exam-sessions';
import { SuspiciousSessionReason } from 'app/openapi/model/suspicious-session-reason';
import type { StudentExamWithIdAndExamAndUser } from 'app/openapi/model/student-exam-with-id-and-exam-and-user';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

@Component({
    // this is intended and an attribute selector because otherwise the rendered table breaks
    selector: '[jhi-suspicious-sessions]',
    templateUrl: './suspicious-sessions.component.html',
    imports: [ArtemisDatePipe, RouterModule, CommonModule],
})
export class SuspiciousSessionsComponent implements OnInit {
    suspiciousSessions = input.required<SuspiciousExamSessions>();
    suspiciousFingerprint = signal(false);
    suspiciousIpAddress = signal(false);
    ngOnInit(): void {
        this.suspiciousFingerprint.set(
            this.isSuspiciousFor(SuspiciousSessionReason.DifferentStudentExamsSameBrowserFingerprint) ||
                this.isSuspiciousFor(SuspiciousSessionReason.SameStudentExamDifferentBrowserFingerprints),
        );
        this.suspiciousIpAddress.set(
            this.isSuspiciousFor(SuspiciousSessionReason.DifferentStudentExamsSameIpAddress) ||
                this.isSuspiciousFor(SuspiciousSessionReason.SameStudentExamDifferentIpAddresses) ||
                this.isSuspiciousFor(SuspiciousSessionReason.IpAddressOutsideOfRange),
        );
    }

    getStudentExamLink(studentExam: StudentExamWithIdAndExamAndUser) {
        const studentExamId = studentExam.id;
        const courseId = studentExam.exam?.course?.id;
        const examId = studentExam.exam?.id;
        return `/course-management/${courseId}/exams/${examId}/student-exams/${studentExamId}`;
    }

    private isSuspiciousFor(reason: SuspiciousSessionReason) {
        return (this.suspiciousSessions().examSessions ?? []).some((session) => session.suspiciousReasons?.includes(reason));
    }
}
