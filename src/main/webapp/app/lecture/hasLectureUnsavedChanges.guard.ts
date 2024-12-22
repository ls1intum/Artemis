import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { LectureUpdateComponent } from 'app/lecture/lecture-update.component';
import { Observable, from, of } from 'rxjs';
import { CloseEditLectureModalComponent } from 'app/lecture/close-edit-lecture-dialog/close-edit-lecture-modal.component';

export const hasLectureUnsavedChangesGuard: CanDeactivateFn<LectureUpdateComponent> = (component: LectureUpdateComponent): Observable<boolean> => {
    if (!component.shouldDisplayDismissWarning || component.isShowingWizardMode) {
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
