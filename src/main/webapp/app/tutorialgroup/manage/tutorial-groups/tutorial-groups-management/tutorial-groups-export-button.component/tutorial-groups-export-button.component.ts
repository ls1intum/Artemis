import {
    ChangeDetectionStrategy,
    Component,
    EffectRef,
    EnvironmentInjector,
    OnDestroy,
    TemplateRef,
    effect,
    inject,
    input,
    output,
    runInInjectionContext,
    signal,
    viewChild,
} from '@angular/core';
import { NgbDropdownButtonItem, NgbDropdownItem, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { EMPTY, Subject, from } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { TutorialGroupExport } from 'app/openapi/models/tutorial-group-export';

@Component({
    selector: 'jhi-tutorial-groups-export-button',
    templateUrl: './tutorial-groups-export-button.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgbDropdownButtonItem, NgbDropdownItem, TranslateDirective, FormsModule],
})
export class TutorialGroupsExportButtonComponent implements OnDestroy {
    private modalService = inject(NgbModal);
    private tutorialGroupsService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private environmentInjector = inject(EnvironmentInjector);

    ngUnsubscribe = new Subject<void>();
    readonly exportDialogRef = viewChild<TemplateRef<any>>('exportDialog');

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
        const modalRef: NgbModalRef = this.modalService.open(this.exportDialogRef(), {
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
        const params = signal({ fields: [...this.selectedFields] });
        const resource = this.tutorialGroupsService.exportTutorialGroupsToCSVResource(this.courseId(), params);
        let exportEffect: EffectRef;
        runInInjectionContext(this.environmentInjector, () => {
            exportEffect = effect(() => {
                const error = resource.error();
                if (error) {
                    this.alertService.error('artemisApp.tutorialGroupExportDialog.failedCSV');
                    this.resetSelections();
                    modal.dismiss('error');
                    exportEffect.destroy();
                    return;
                }

                const blob = resource.value();
                if (!blob) {
                    return;
                }
                const a = document.createElement('a');
                const objectUrl = URL.createObjectURL(blob);
                a.href = objectUrl;
                a.download = 'tutorial-groups.csv';
                a.click();
                URL.revokeObjectURL(objectUrl);
                this.resetSelections();
                modal.close();
                exportEffect.destroy();
            });
        });
    }

    exportJSON(modal: NgbModalRef) {
        const params = signal({ fields: [...this.selectedFields] });
        const resource = this.tutorialGroupsService.exportTutorialGroupsToJSONResource(this.courseId(), params);
        let exportEffect: EffectRef;
        runInInjectionContext(this.environmentInjector, () => {
            exportEffect = effect(() => {
                const error = resource.error();
                if (error) {
                    this.alertService.error('artemisApp.tutorialGroupExportDialog.failedJSON');
                    this.resetSelections();
                    modal.dismiss('error');
                    exportEffect.destroy();
                    return;
                }

                const data = resource.value();
                if (!data) {
                    return;
                }
                const blob = new Blob([JSON.stringify(data as Array<TutorialGroupExport>)], { type: 'application/json' });
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = 'tutorial_groups.json';
                a.click();
                window.URL.revokeObjectURL(url);
                this.resetSelections();
                modal.close();
                exportEffect.destroy();
            });
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
