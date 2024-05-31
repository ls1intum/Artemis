import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnDestroy, Output, TemplateRef, ViewChild } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { EMPTY, Subject, from } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-tutorial-groups-export-button',
    templateUrl: './tutorial-groups-export-button.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorialGroupsExportButtonComponent implements OnDestroy {
    ngUnsubscribe = new Subject<void>();

    @ViewChild('exportDialog') exportDialogRef: TemplateRef<any>;

    @Input() courseId: number;

    @Output() exportFinished: EventEmitter<void> = new EventEmitter();

    selectedFields: string[] = [];
    availableFields = [
        { name: 'ID', value: 'ID' },
        { name: 'Title', value: 'Title' },
        { name: 'Day of Week', value: 'Day of Week' },
        { name: 'Start Time', value: 'Start Time' },
        { name: 'Location', value: 'Location' },
        { name: 'Campus', value: 'Campus' },
        { name: 'Language', value: 'Language' },
        { name: 'Additional Information', value: 'Additional Information' },
        { name: 'Capacity', value: 'Capacity' },
        { name: 'Is Online', value: 'Is Online' },
    ];

    constructor(
        private modalService: NgbModal,
        private tutorialGroupsService: TutorialGroupsService,
        private alertService: AlertService,
    ) {}

    openExportDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(this.exportDialogRef, {
            size: 'lg',
            scrollable: false,
            backdrop: 'static',
            animation: false,
        });

        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                this.exportFinished.emit();
            });
    }

    onFieldSelectionChange(event: Event) {
        const checkbox = event.target as HTMLInputElement;
        const value = checkbox.value;

        if (checkbox.checked) {
            this.selectedFields.push(value);
        } else {
            const index = this.selectedFields.indexOf(value);
            if (index > -1) {
                this.selectedFields.splice(index, 1);
            }
        }
    }

    exportCSV(modal: NgbModalRef) {
        this.tutorialGroupsService.exportTutorialGroupsToCSV(this.courseId, this.selectedFields).subscribe({
            next: (blob: Blob) => {
                const a = document.createElement('a');
                const objectUrl = URL.createObjectURL(blob);
                a.href = objectUrl;
                a.download = 'tutorial-groups.csv';
                a.click();
                URL.revokeObjectURL(objectUrl);
                this.selectedFields = [];
                modal.close();
            },
            error: (res: HttpErrorResponse) => {
                onError(this.alertService, res);
                this.selectedFields = [];
                modal.dismiss('error');
            },
        });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
