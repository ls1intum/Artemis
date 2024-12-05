import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { CloseEditLectureDialogComponent } from 'app/lecture/close-edit-lecture-dialog.component.ts/close-edit-lecture-dialog.component';
import { LectureUpdateComponent } from 'app/lecture/lecture-update.component';
import { Observable, from, of } from 'rxjs';

export const hasLectureUnsavedChangesGuard: CanDeactivateFn<LectureUpdateComponent> = (component: LectureUpdateComponent): Observable<boolean> => {
    if (component.isChangeMadeToTitleOrPeriodSection) {
        const modalService = inject(NgbModal);

        const modalRef: NgbModalRef = modalService.open(CloseEditLectureDialogComponent, {
            size: 'lg',
            backdrop: 'static',
            animation: true,
        });
        return from(modalRef.result);
    }

    return of(true);
};
