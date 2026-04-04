import { ChangeDetectionStrategy, Component, OnDestroy, inject, input, output, signal } from '@angular/core';
import { NgbDropdownButtonItem, NgbDropdownItem } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialGroupExportData } from 'app/openapi/model/tutorialGroupExportData';
import { map } from 'rxjs/operators';

@Component({
    selector: 'jhi-tutorial-groups-export-button',
    templateUrl: './tutorial-groups-export-button.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgbDropdownButtonItem, NgbDropdownItem, TranslateDirective, FormsModule, DialogModule, ArtemisTranslatePipe],
})
export class TutorialGroupsExportButtonComponent implements OnDestroy {
    private tutorialGroupApiService = inject(TutorialGroupApiService);
    private alertService = inject(AlertService);

    ngUnsubscribe = new Subject<void>();

    readonly dialogVisible = signal<boolean>(false);

    courseId = input.required<number>();

    readonly exportFinished = output<void>();

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
        this.resetSelections();
        this.dialogVisible.set(true);
    }

    closeDialog() {
        this.dialogVisible.set(false);
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

    exportCSV() {
        this.tutorialGroupApiService.exportTutorialGroupsToCSV(this.courseId(), this.selectedFields).subscribe({
            next: (blob: Blob) => {
                const a = document.createElement('a');
                const objectUrl = URL.createObjectURL(blob);
                a.href = objectUrl;
                a.download = 'tutorial-groups.csv';
                a.click();
                URL.revokeObjectURL(objectUrl);
                this.resetSelections();
                this.closeDialog();
                this.exportFinished.emit();
            },
            error: () => {
                this.alertService.error('artemisApp.tutorialGroupExportDialog.failedCSV');
                this.resetSelections();
                this.closeDialog();
            },
        });
    }

    exportJSON() {
        this.tutorialGroupApiService
            .exportTutorialGroupsToJSON(this.courseId(), this.selectedFields)
            .pipe(map((data: TutorialGroupExportData[]) => JSON.stringify(data)))
            .subscribe({
                next: (response) => {
                    const blob = new Blob([response], { type: 'application/json' });
                    const url = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = 'tutorial_groups.json';
                    a.click();
                    window.URL.revokeObjectURL(url);
                    this.resetSelections();
                    this.closeDialog();
                    this.exportFinished.emit();
                },
                error: () => {
                    this.alertService.error('artemisApp.tutorialGroupExportDialog.failedJSON');
                    this.resetSelections();
                    this.closeDialog();
                },
            });
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
