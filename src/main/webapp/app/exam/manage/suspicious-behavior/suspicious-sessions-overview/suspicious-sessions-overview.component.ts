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
            case SuspiciousSessionReason.SAME_IP_ADDRESS:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.sameIpAddress';
            case SuspiciousSessionReason.SAME_BROWSER_FINGERPRINT:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.sameBrowserFingerprint';
        }
    }
    ngOnInit(): void {
        this.suspiciousSessions = cloneDeep(history.state.suspiciousSessions);
    }
}
