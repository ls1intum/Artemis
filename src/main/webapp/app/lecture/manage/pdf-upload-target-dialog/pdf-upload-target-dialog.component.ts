import { Component, OnInit, inject, signal } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFilePdf, faFolderOpen, faFolderPlus } from '@fortawesome/free-solid-svg-icons';

export interface PdfUploadTarget {
    targetType: 'new' | 'existing';
    lectureId?: number;
    newLectureTitle?: string;
}

@Component({
    selector: 'jhi-pdf-upload-target-dialog',
    standalone: true,
    imports: [FormsModule, TranslateDirective, ArtemisTranslatePipe, FaIconComponent],
    templateUrl: './pdf-upload-target-dialog.component.html',
    styleUrls: ['./pdf-upload-target-dialog.component.scss'],
})
export class PdfUploadTargetDialogComponent implements OnInit {
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);

    protected readonly faFilePdf = faFilePdf;
    protected readonly faFolderPlus = faFolderPlus;
    protected readonly faFolderOpen = faFolderOpen;

    lectures = signal<Lecture[]>([]);
    uploadedFiles = signal<File[]>([]);

    targetType = signal<'new' | 'existing'>('new');
    selectedLectureId = signal<number | undefined>(undefined);
    newLectureTitle = signal<string>('');

    ngOnInit(): void {
        const data = this.dialogConfig.data;
        if (data?.lectures) {
            this.lectures.set(data.lectures);
        }
        if (data?.uploadedFiles) {
            this.initializeWithFiles(data.uploadedFiles);
        }
    }

    /**
     * Initialize the dialog with uploaded files
     * Derives a default lecture title from the first filename
     */
    private initializeWithFiles(files: File[]): void {
        this.uploadedFiles.set(files);
        if (files.length > 0) {
            this.newLectureTitle.set(this.deriveLectureTitleFromFiles(files));
        }
    }

    /**
     * Derive a lecture title from uploaded files
     * Uses the first filename, cleaned up
     */
    private deriveLectureTitleFromFiles(files: File[]): string {
        const firstName = files[0].name;
        // Remove .pdf extension and clean up
        return firstName
            .replace(/\.pdf$/i, '')
            .replace(/[_-]/g, ' ')
            .replace(/\s+/g, ' ')
            .trim();
    }

    onTargetTypeChange(type: 'new' | 'existing'): void {
        this.targetType.set(type);
        if (type === 'new') {
            this.selectedLectureId.set(undefined);
        }
    }

    onLectureSelect(event: Event): void {
        const select = event.target as HTMLSelectElement;
        const value = select.value;
        this.selectedLectureId.set(value ? Number(value) : undefined);
    }

    isValid(): boolean {
        if (this.targetType() === 'new') {
            return this.newLectureTitle().trim().length > 0;
        }
        return this.selectedLectureId() !== undefined;
    }

    confirm(): void {
        if (!this.isValid()) {
            return;
        }

        const result: PdfUploadTarget = {
            targetType: this.targetType(),
            lectureId: this.selectedLectureId(),
            newLectureTitle: this.targetType() === 'new' ? this.newLectureTitle().trim() : undefined,
        };
        this.dialogRef.close(result);
    }

    cancel(): void {
        this.dialogRef.close();
    }
}
