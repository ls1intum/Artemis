import { Component, OnInit, inject, signal } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-close-edit-lecture-modal',
    imports: [TranslateDirective, FontAwesomeModule],
    templateUrl: './close-edit-lecture-modal.component.html',
})
export class CloseEditLectureModalComponent implements OnInit {
    protected readonly faTimes = faTimes;

    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);

    hasUnsavedChangesInTitleSection = signal(false);
    hasUnsavedChangesInPeriodSection = signal(false);

    ngOnInit(): void {
        const data = this.dialogConfig.data;
        this.hasUnsavedChangesInTitleSection.set(data?.hasUnsavedChangesInTitleSection ?? false);
        this.hasUnsavedChangesInPeriodSection.set(data?.hasUnsavedChangesInPeriodSection ?? false);
    }

    closeWindow(isCloseConfirmed: boolean): void {
        this.dialogRef.close(isCloseConfirmed);
    }
}
