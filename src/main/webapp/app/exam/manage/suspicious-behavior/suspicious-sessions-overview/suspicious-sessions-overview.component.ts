import { Component, OnInit, signal } from '@angular/core';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/exam/shared/entities/exam-session.model';
import { SuspiciousSessionsComponent } from 'app/exam/manage/suspicious-behavior/suspicious-sessions/suspicious-sessions.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';

@Component({
    selector: 'jhi-suspicious-sessions-overview',
    templateUrl: './suspicious-sessions-overview.component.html',
    styleUrls: ['./suspicious-sessions-overview.component.scss'],
    imports: [SuspiciousSessionsComponent, TranslateDirective, ArtemisTranslatePipe, CourseTitleBarTitleDirective],
})
export class SuspiciousSessionsOverviewComponent implements OnInit {
    suspiciousSessions = signal<SuspiciousExamSessions[]>([]);
    ipSubnet = signal<string | undefined>(undefined);

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
        this.suspiciousSessions.set(deepClone(history.state.suspiciousSessions));
        this.ipSubnet.set(history.state.ipSubnet);
    }
}
