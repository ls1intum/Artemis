import { Component, EventEmitter, Input, OnDestroy, Output, TemplateRef, ViewChild } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupsRegistrationImportDialog } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/tutorial-groups-management/tutorial-groups-import-dialog/tutorial-groups-registration-import-dialog.component';
import { Subject, from } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
    selector: 'jhi-tutorial-groups-import-button',
    templateUrl: './tutorial-groups-import-button.component.html',
})
export class TutorialGroupsImportButtonComponent implements OnDestroy {
    ngUnsubscribe = new Subject<void>();

    @ViewChild('warning')
    public warningRef: TemplateRef<any>;

    @Input() courseId: number;

    @Output() importFinished: EventEmitter<void> = new EventEmitter();

    constructor(private modalService: NgbModal) {}

    openTutorialGroupImportDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(TutorialGroupsRegistrationImportDialog, { size: 'xl', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.courseId = this.courseId;

        from(modalRef.result)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe(() => {
                this.openWarning();
            });
    }

    openWarning() {
        if (this.warningRef) {
            const modalRef: NgbModalRef = this.modalService.open(this.warningRef, { centered: true });
            from(modalRef.result)
                .pipe(takeUntil(this.ngUnsubscribe))
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
