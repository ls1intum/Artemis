import { ChangeDetectionStrategy, Component, computed, input, linkedSignal, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFilePdf, faFolderOpen, faFolderPlus } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiButtonDirective,
    TumAetUiDialogComponent,
    TumAetUiFormFieldComponent,
    TumAetUiInputDirective,
    TumAetUiRadioButtonComponent,
    TumAetUiSelectComponent,
} from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';

export type PdfUploadTargetType = 'new' | 'existing';

export interface PdfUploadTarget {
    targetType: PdfUploadTargetType;
    lectureId?: number;
    newLectureTitle?: string;
}

/**
 * Asks where PDFs dropped on the lecture list go: into a new lecture, or into an existing one. Every choice starts over
 * when other files are dropped.
 */
@Component({
    selector: 'jhi-pdf-upload-target-dialog',
    imports: [
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiDialogComponent,
        TumAetUiFormFieldComponent,
        TumAetUiInputDirective,
        TumAetUiRadioButtonComponent,
        TumAetUiSelectComponent,
    ],
    templateUrl: './pdf-upload-target-dialog.component.html',
    styleUrl: './pdf-upload-target-dialog.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfUploadTargetDialogComponent {
    protected readonly faFilePdf = faFilePdf;
    protected readonly faFolderPlus = faFolderPlus;
    protected readonly faFolderOpen = faFolderOpen;

    readonly visible = model(false);
    readonly lectures = input<Lecture[]>([]);
    readonly uploadedFiles = input<File[]>([]);

    readonly targetSelected = output<PdfUploadTarget>();

    readonly targetType = linkedSignal<File[], PdfUploadTargetType>({ source: this.uploadedFiles, computation: () => 'new' });
    readonly selectedLectureId = linkedSignal<File[], number | undefined>({ source: this.uploadedFiles, computation: () => undefined });
    /** Starts as the name of the first file, cleaned up, which usually is the topic of the lecture. */
    readonly newLectureTitle = linkedSignal(() => this.deriveLectureTitleFromFiles(this.uploadedFiles()));

    readonly isValid = computed(() => (this.targetType() === 'new' ? this.newLectureTitle().trim().length > 0 : this.selectedLectureId() !== undefined));

    private deriveLectureTitleFromFiles(files: File[]): string {
        const firstName = files[0]?.name ?? '';
        return firstName
            .replace(/\.pdf$/i, '')
            .replace(/[_-]/g, ' ')
            .replace(/\s+/g, ' ')
            .trim();
    }

    onTargetTypeChange(type: PdfUploadTargetType): void {
        this.targetType.set(type);
        if (type === 'new') {
            this.selectedLectureId.set(undefined);
        }
    }

    confirm(): void {
        if (!this.isValid()) {
            return;
        }
        const targetType = this.targetType();
        this.targetSelected.emit({
            targetType,
            lectureId: targetType === 'existing' ? this.selectedLectureId() : undefined,
            newLectureTitle: targetType === 'new' ? this.newLectureTitle().trim() : undefined,
        });
        this.visible.set(false);
    }

    cancel(): void {
        this.visible.set(false);
    }
}
