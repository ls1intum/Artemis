import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Observable, from, of } from 'rxjs';
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
        const modalService = inject(NgbModal);

        const modalRef: NgbModalRef = modalService.open(CloseEditLectureModalComponent, {
            size: 'lg',
            backdrop: 'static',
            animation: true,
        });
        modalRef.componentInstance.hasUnsavedChangesInTitleSection = component.isChangeMadeToTitleSection();
        modalRef.componentInstance.hasUnsavedChangesInPeriodSection = component.isChangeMadeToPeriodSection();

        return from(modalRef.result);
    }

    return of(true);
};
