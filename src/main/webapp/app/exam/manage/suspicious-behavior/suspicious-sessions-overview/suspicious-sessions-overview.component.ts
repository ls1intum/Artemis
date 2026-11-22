import { Component, OnInit, signal } from '@angular/core';
import { TumAetUiTableDirective } from '@tumaet/ui-angular';
import type { SuspiciousExamSessions } from 'app/openapi/model/suspicious-exam-sessions';
import { SuspiciousSessionReason } from 'app/openapi/model/suspicious-session-reason';
import { SuspiciousSessionsComponent } from 'app/exam/manage/suspicious-behavior/suspicious-sessions/suspicious-sessions.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';

@Component({
    selector: 'jhi-suspicious-sessions-overview',
    templateUrl: './suspicious-sessions-overview.component.html',
    imports: [SuspiciousSessionsComponent, TumAetUiTableDirective, TranslateDirective, ArtemisTranslatePipe, CourseTitleBarTitleDirective],
})
export class SuspiciousSessionsOverviewComponent implements OnInit {
    suspiciousSessions = signal<SuspiciousExamSessions[]>([]);
    ipSubnet = signal<string | undefined>(undefined);

    mapEnumToTranslationString(reason: SuspiciousSessionReason) {
        switch (reason) {
            case SuspiciousSessionReason.DifferentStudentExamsSameIpAddress:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.sameIpAddressDifferentStudentExams';
            case SuspiciousSessionReason.DifferentStudentExamsSameBrowserFingerprint:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.sameBrowserFingerprintDifferentStudentExams';
            case SuspiciousSessionReason.SameStudentExamDifferentIpAddresses:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.differentIpAddressesSameStudentExam';
            case SuspiciousSessionReason.SameStudentExamDifferentBrowserFingerprints:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.differentBrowserFingerprintsSameStudentExam';
            case SuspiciousSessionReason.IpAddressOutsideOfRange:
                return 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.ipOutsideOfRange';
        }
    }

    ngOnInit(): void {
        this.suspiciousSessions.set(deepClone(history.state.suspiciousSessions));
        this.ipSubnet.set(history.state.ipSubnet);
    }
}
