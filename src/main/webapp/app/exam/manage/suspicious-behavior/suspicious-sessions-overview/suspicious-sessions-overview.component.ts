import { Component, OnInit } from '@angular/core';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/entities/exam/exam-session.model';
import { cloneDeep } from 'lodash-es';

@Component({
    selector: 'jhi-suspicious-sessions-overview',
    templateUrl: './suspicious-sessions-overview.component.html',
    styleUrls: ['./suspicious-sessions-overview.component.scss'],
})
export class SuspiciousSessionsOverviewComponent implements OnInit {
    suspiciousSessions: SuspiciousExamSessions[] = [];
    ipSubnet?: string;

    mapEnumToTranslationString(reason: SuspiciousSessionReason) {
        switch (reason) {
            case SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.sameIpAddressDifferentStudentExams';
            case SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.sameBrowserFingerprintDifferentStudentExams';
            case SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.differentIpAddressesSameStudentExam';
            case SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.differentBrowserFingerprintsSameStudentExam';
            case SuspiciousSessionReason.IP_ADDRESS_OUTSIDE_OF_RANGE:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.ipOutsideOfRange';
        }
    }

    ngOnInit(): void {
        this.suspiciousSessions = cloneDeep(history.state.suspiciousSessions);
        this.ipSubnet = history.state.ipSubnet;
    }
}
