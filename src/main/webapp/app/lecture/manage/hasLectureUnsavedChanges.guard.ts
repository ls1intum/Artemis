import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { CloseEditLectureModalComponent } from 'app/lecture/manage/close-edit-lecture-modal/close-edit-lecture-modal.component';

/**
 * Interface for components that can have unsaved lecture changes.
 * Extracted to allow testing without importing heavy component dependencies.
 */
export interface LectureUnsavedChangesComponent {
    shouldDisplayDismissWarning: boolean;
    isChangeMadeToTitleOrPeriodSection: boolean;
    isChangeMadeToTitleSection(): boolean;
    isChangeMadeToPeriodSection(): boolean;
}

export const hasLectureUnsavedChangesGuard: CanDeactivateFn<LectureUnsavedChangesComponent> = (component: LectureUnsavedChangesComponent): Observable<boolean> => {
    if (!component.shouldDisplayDismissWarning) {
        return of(true);
    }

    if (component.isChangeMadeToTitleOrPeriodSection) {
        const dialogService = inject(DialogService);
        const translateService = inject(TranslateService);

        const dialogRef = dialogService.open(CloseEditLectureModalComponent, {
            header: translateService.instant('artemisApp.lecture.dismissChangesModal.title'),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: false,
            dismissableMask: false,
            data: {
                hasUnsavedChangesInTitleSection: component.isChangeMadeToTitleSection(),
                hasUnsavedChangesInPeriodSection: component.isChangeMadeToPeriodSection(),
            },
        });

        return dialogRef!.onClose.pipe(map((result: boolean | undefined) => result === true));
    }

    return of(true);
};
