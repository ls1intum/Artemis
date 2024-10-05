import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnDestroy, Output, TemplateRef, ViewChild, inject } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupsRegistrationImportDialogComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/tutorial-groups-management/tutorial-groups-import-dialog/tutorial-groups-registration-import-dialog.component';
import { EMPTY, Subject, from } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';

@Component({
    selector: 'jhi-tutorial-groups-import-button',
    templateUrl: './tutorial-groups-import-button.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorialGroupsImportButtonComponent implements OnDestroy {
    private modalService = inject(NgbModal);

    ngUnsubscribe = new Subject<void>();

    @ViewChild('warning')
    public warningRef: TemplateRef<any>;

    @Input() courseId: number;

    @Output() importFinished: EventEmitter<void> = new EventEmitter();

    openTutorialGroupImportDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(TutorialGroupsRegistrationImportDialogComponent, {
            size: 'xl',
            scrollable: false,
            backdrop: 'static',
            animation: false,
        });
        modalRef.componentInstance.courseId = this.courseId;

        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                this.openWarning();
            });
    }

    openWarning() {
        if (this.warningRef) {
            const modalRef: NgbModalRef = this.modalService.open(this.warningRef, { centered: true, animation: false });
            from(modalRef.result)
                .pipe(
                    catchError(() => EMPTY),
                    takeUntil(this.ngUnsubscribe),
                )
                .subscribe(() => {
                    this.importFinished.emit();
                });
        }
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
