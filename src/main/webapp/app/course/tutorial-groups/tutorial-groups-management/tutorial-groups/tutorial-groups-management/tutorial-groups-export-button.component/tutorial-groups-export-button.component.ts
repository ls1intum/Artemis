import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnDestroy, Output, TemplateRef, ViewChild, inject } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { EMPTY, Subject, from } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-tutorial-groups-export-button',
    templateUrl: './tutorial-groups-export-button.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorialGroupsExportButtonComponent implements OnDestroy {
    private modalService = inject(NgbModal);
    private tutorialGroupsService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);

    ngUnsubscribe = new Subject<void>();

    @ViewChild('exportDialog') exportDialogRef: TemplateRef<any>;

    @Input() courseId: number;

    @Output() exportFinished: EventEmitter<void> = new EventEmitter();

    selectAll = false;

    selectedFields: string[] = [];
    availableFields = [
        { value: 'ID', selected: false },
        { value: 'Title', selected: false },
        { value: 'Day of Week', selected: false },
        { value: 'Start Time', selected: false },
        { value: 'End Time', selected: false },
        { value: 'Location', selected: false },
        { value: 'Campus', selected: false },
        { value: 'Language', selected: false },
        { value: 'Additional Information', selected: false },
        { value: 'Capacity', selected: false },
        { value: 'Is Online', selected: false },
        { value: 'Students', selected: false },
    ];

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

    toggleSelectAll() {
        this.selectAll = !this.selectAll;
        this.availableFields.forEach((field) => (field.selected = this.selectAll));
        this.updateSelectedFields();
    }

    onFieldSelectionChange(field: any) {
        field.selected = !field.selected;
        this.selectAll = this.areAllFieldsSelected();
        this.updateSelectedFields();
    }

    updateSelectedFields() {
        this.selectedFields = this.availableFields.filter((field) => field.selected).map((field) => field.value);
    }

    areAllFieldsSelected(): boolean {
        return this.availableFields.every((field) => field.selected);
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
                this.resetSelections();
                modal.close();
            },
            error: () => {
                this.alertService.error('artemisApp.tutorialGroupExportDialog.failedCSV');
                this.resetSelections();
                modal.dismiss('error');
            },
        });
    }

    exportJSON(modal: NgbModalRef) {
        this.tutorialGroupsService.exportToJson(this.courseId, this.selectedFields).subscribe(
            (response) => {
                const blob = new Blob([response], { type: 'application/json' });
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = 'tutorial_groups.json';
                a.click();
                window.URL.revokeObjectURL(url);
                this.resetSelections();
                modal.close();
            },
            () => {
                this.alertService.error('artemisApp.tutorialGroupExportDialog.failedJSON');
                this.resetSelections();
                modal.dismiss('error');
            },
        );
    }

    private resetSelections() {
        this.selectedFields = [];
        this.availableFields.forEach((field) => (field.selected = false));
        this.selectAll = false;
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
