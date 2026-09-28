import { ChangeDetectionStrategy, Component, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCloudUploadAlt, faFilePdf } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AlertService } from 'app/foundation/service/alert.service';
import { MAX_FILE_SIZE } from 'app/foundation/constants/input.constants';

let nextDropZoneId = 0;

/** An area to drop PDFs on, with a button that selects them instead; each PDF becomes one content item. */
@Component({
    selector: 'jhi-pdf-drop-zone',
    imports: [FaIconComponent, TumAetUiButtonDirective, TranslateDirective],
    templateUrl: './pdf-drop-zone.component.html',
    styleUrl: './pdf-drop-zone.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfDropZoneComponent {
    private readonly alertService = inject(AlertService);

    filesDropped = output<File[]>();
    disabled = input<boolean>(false);
    /** Translation key that says what dropping PDFs here does. */
    /** Translation key of the heading; not named title, which would also become the tooltip of the element. */
    readonly heading = input<string>('artemisApp.lecture.pdfUpload.dropZoneTitle');

    protected readonly hintId = `pdf-drop-zone-hint-${nextDropZoneId++}`;

    fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');

    protected readonly faCloudUploadAlt = faCloudUploadAlt;
    protected readonly faFilePdf = faFilePdf;

    isDragOver = signal(false);

    onDragOver(event: DragEvent): void {
        event.preventDefault();
        event.stopPropagation();
        if (!this.disabled()) {
            this.isDragOver.set(true);
        }
    }

    onDragLeave(event: DragEvent): void {
        event.preventDefault();
        event.stopPropagation();
        this.isDragOver.set(false);
    }

    onDrop(event: DragEvent): void {
        event.preventDefault();
        event.stopPropagation();
        this.isDragOver.set(false);

        if (this.disabled()) {
            return;
        }

        const files = event.dataTransfer?.files;
        if (files) {
            this.handleFiles(files);
        }
    }

    onFileInputChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (input.files && !this.disabled()) {
            this.handleFiles(input.files);
            // Reset input so the same file can be selected again
            input.value = '';
        }
    }

    onClick(): void {
        if (!this.disabled()) {
            this.fileInput().nativeElement.click();
        }
    }

    private handleFiles(fileList: FileList): void {
        const pdfFiles: File[] = [];
        let hasOversizedFiles = false;

        for (let i = 0; i < fileList.length; i++) {
            const file = fileList[i];
            if (file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')) {
                if (file.size > MAX_FILE_SIZE) {
                    hasOversizedFiles = true;
                } else {
                    pdfFiles.push(file);
                }
            }
        }

        if (hasOversizedFiles) {
            this.alertService.error('artemisApp.lecture.pdfUpload.fileTooLarge');
        }

        if (pdfFiles.length > 0) {
            this.filesDropped.emit(pdfFiles);
        }
    }
}
