import { Component, OnInit } from '@angular/core';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/entities/exam-session.model';
import { cloneDeep } from 'lodash-es';

@Component({
    selector: 'jhi-suspicious-sessions-overview',
    templateUrl: './suspicious-sessions-overview.component.html',
    styleUrls: ['./suspicious-sessions-overview.component.scss'],
})
export class SuspiciousSessionsOverviewComponent implements OnInit {
    suspiciousSessions: SuspiciousExamSessions[] = [];

    mapEnumToTranslationString(reason: SuspiciousSessionReason) {
        switch (reason) {
            case SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.sameIpAddress';
            case SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.sameBrowserFingerprint';
            case SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.differentIpAddress';
            case SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.differentBrowserFingerprint';
            case SuspiciousSessionReason.IP_OUTSIDE_OF_RANGE:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.ipOutsideOfRange';
        }
    }

    ngOnInit(): void {
        this.suspiciousSessions = cloneDeep(history.state.suspiciousSessions);
    }
}
