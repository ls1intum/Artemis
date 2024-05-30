import { __decorate, __metadata } from 'tslib';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, TemplateRef, ViewChild } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupsRegistrationImportDialogComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/tutorial-groups-management/tutorial-groups-import-dialog/tutorial-groups-registration-import-dialog.component';
import { EMPTY, Subject, from } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';
let TutorialGroupsExportButtonComponent = class TutorialGroupsExportButtonComponent {
    modalService;
    ngUnsubscribe = new Subject();
    warningRef;
    courseId;
    exportFinished = new EventEmitter();
    constructor(modalService) {
        this.modalService = modalService;
    }
    openTutorialGroupExportDialog(event) {
        event.stopPropagation();
        const modalRef = this.modalService.open(TutorialGroupsRegistrationExportDialogComponent, {
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
            const modalRef = this.modalService.open(this.warningRef, { centered: true, animation: false });
            from(modalRef.result)
                .pipe(
                    catchError(() => EMPTY),
                    takeUntil(this.ngUnsubscribe),
                )
                .subscribe(() => {
                    this.exportFinished.emit();
                });
        }
    }
    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
};
__decorate([ViewChild('warning'), __metadata('design:type', TemplateRef)], TutorialGroupsExportButtonComponent.prototype, 'warningRef', void 0);
__decorate([Input(), __metadata('design:type', Number)], TutorialGroupsExportButtonComponent.prototype, 'courseId', void 0);
__decorate([Output(), __metadata('design:type', EventEmitter)], TutorialGroupsExportButtonComponent.prototype, 'exportFinished', void 0);
TutorialGroupsExportButtonComponent = __decorate(
    [
        Component({
            selector: 'jhi-tutorial-groups-export-button',
            templateUrl: './tutorial-groups-export-button.component.html',
            changeDetection: ChangeDetectionStrategy.OnPush,
        }),
        __metadata('design:paramtypes', [NgbModal]),
    ],
    TutorialGroupsExportButtonComponent,
);
export { TutorialGroupsExportButtonComponent };
//# sourceMappingURL=tutorial-groups-import-button.component.js.map
