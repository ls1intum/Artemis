import { CommonModule } from '@angular/common';
import { Component, OnInit, input, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/exam/shared/entities/exam-session.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({
    // this is intended and an attribute selector because otherwise the rendered table breaks
    selector: '[jhi-suspicious-sessions]',
    templateUrl: './suspicious-sessions.component.html',
    styleUrls: ['./suspicious-sessions.component.scss'],
    imports: [ArtemisDatePipe, RouterModule, CommonModule],
})
export class SuspiciousSessionsComponent implements OnInit {
    suspiciousSessions = input.required<SuspiciousExamSessions>();
    suspiciousFingerprint = signal(false);
    suspiciousIpAddress = signal(false);
    ngOnInit(): void {
        this.suspiciousFingerprint.set(
            this.isSuspiciousFor(SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT) ||
                this.isSuspiciousFor(SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS),
        );
        this.suspiciousIpAddress.set(
            this.isSuspiciousFor(SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS) ||
                this.isSuspiciousFor(SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES) ||
                this.isSuspiciousFor(SuspiciousSessionReason.IP_ADDRESS_OUTSIDE_OF_RANGE),
        );
    }

    getStudentExamLink(studentExam: StudentExam) {
        const studentExamId = studentExam.id;
        const courseId = studentExam.exam?.course?.id;
        const examId = studentExam.exam?.id;
        return `/course-management/${courseId}/exams/${examId}/student-exams/${studentExamId}`;
    }

    private isSuspiciousFor(reason: SuspiciousSessionReason) {
        return this.suspiciousSessions().examSessions.some((session) => session.suspiciousReasons.includes(reason));
    }
}
