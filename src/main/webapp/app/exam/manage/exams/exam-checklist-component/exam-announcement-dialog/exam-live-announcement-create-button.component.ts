import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { Component, OnDestroy, OnInit, inject, input } from '@angular/core';
import { faBullhorn } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Subscription } from 'rxjs';

import { Exam } from 'app/exam/shared/entities/exam.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { ExamLiveAnnouncementCreateModalComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-modal.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-exam-live-announcement-create-button',
    templateUrl: './exam-live-announcement-create-button.component.html',
    imports: [FaIconComponent, TranslateDirective],
})
export class ExamLiveAnnouncementCreateButtonComponent implements OnInit, OnDestroy {
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);
    alertService = inject(AlertService);

    exam = input.required<Exam>();

    faBullhorn = faBullhorn;
    announcementCreationAllowed = false;

    private dialogRef: DynamicDialogRef | null | undefined;
    private timeoutRef: any;
    private subscription: Subscription | undefined;

    ngOnInit() {
        this.checkAnnouncementCreationAllowed();
    }

    ngOnDestroy() {
        if (this.timeoutRef) {
            clearTimeout(this.timeoutRef);
        }
        this.subscription?.unsubscribe();
    }

    private checkAnnouncementCreationAllowed() {
        const now = dayjs();

        this.announcementCreationAllowed = !!this.exam().visibleDate?.isBefore(now);

        // Run the check again at the visible date
        if (!this.announcementCreationAllowed) {
            const nextCheckTimeout = this.exam().visibleDate?.diff(now);
            if (nextCheckTimeout) {
                this.timeoutRef = setTimeout(this.checkAnnouncementCreationAllowed.bind(this), nextCheckTimeout);
            }
        }
    }

    openDialog(event: MouseEvent) {
        event.preventDefault();
        this.alertService.closeAll();
        this.dialogRef = this.dialogService.open(ExamLiveAnnouncementCreateModalComponent, {
            header: this.translateService.instant('artemisApp.examManagement.announcementCreate.title'),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            data: {
                examId: this.exam().id,
                courseId: this.exam().course!.id!,
            },
        });

        this.subscription = this.dialogRef?.onClose.subscribe(() => (this.dialogRef = undefined));
    }
}
