import { Component, OnDestroy, OnInit, inject, input, signal } from '@angular/core';
import { faBullhorn } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';

import { Exam } from 'app/exam/shared/entities/exam.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { ExamLiveAnnouncementCreateModalComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-modal.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-exam-live-announcement-create-button',
    templateUrl: './exam-live-announcement-create-button.component.html',
    imports: [FaIconComponent, TranslateDirective, TumAetUiButtonDirective, ExamLiveAnnouncementCreateModalComponent],
})
export class ExamLiveAnnouncementCreateButtonComponent implements OnInit, OnDestroy {
    alertService = inject(AlertService);

    exam = input.required<Exam>();

    faBullhorn = faBullhorn;
    readonly announcementCreationAllowed = signal(false);

    readonly dialogVisible = signal(false);
    private timeoutRef: ReturnType<typeof setTimeout> | undefined;

    ngOnInit() {
        this.checkAnnouncementCreationAllowed();
    }

    ngOnDestroy() {
        if (this.timeoutRef) {
            clearTimeout(this.timeoutRef);
        }
    }

    private checkAnnouncementCreationAllowed() {
        const now = dayjs();

        this.announcementCreationAllowed.set(!!this.exam().visibleDate?.isBefore(now));

        // Run the check again at the visible date
        if (!this.announcementCreationAllowed()) {
            const nextCheckTimeout = this.exam().visibleDate?.diff(now);
            if (nextCheckTimeout) {
                this.timeoutRef = setTimeout(this.checkAnnouncementCreationAllowed.bind(this), nextCheckTimeout);
            }
        }
    }

    openDialog(event: MouseEvent) {
        event.preventDefault();
        this.alertService.closeAll();
        this.dialogVisible.set(true);
    }
}
