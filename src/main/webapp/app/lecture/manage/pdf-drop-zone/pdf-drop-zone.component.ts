import { Component, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCloudUploadAlt, faFilePdf } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { MAX_FILE_SIZE } from 'app/foundation/constants/input.constants';

let nextHintId = 0;

@Component({
    selector: 'jhi-pdf-drop-zone',
    standalone: true,
    imports: [NgClass, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
    templateUrl: './pdf-drop-zone.component.html',
    styleUrls: ['./pdf-drop-zone.component.scss'],
})
export class PdfDropZoneComponent {
    private readonly alertService = inject(AlertService);

    filesDropped = output<File[]>();
    disabled = input<boolean>(false);
    /** When false, the file browser allows a single file and only the first valid PDF of a drop is emitted. */
    multiple = input<boolean>(true);
    /** Translation key of the headline, which also serves as the accessible name of the drop zone. */
    titleKey = input<string>('artemisApp.lecture.pdfUpload.dropZoneTitle');
    /** Translation key of the line below the headline. */
    hintKey = input<string>('artemisApp.lecture.pdfUpload.dropZoneHint');

    fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');

    protected readonly faCloudUploadAlt = faCloudUploadAlt;
    protected readonly faFilePdf = faFilePdf;
    /** Several drop zones can be on one page, so each needs its own id to point aria-describedby at its hint. */
    protected readonly hintId = `pdf-drop-zone-hint-${nextHintId++}`;

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
            this.filesDropped.emit(this.multiple() ? pdfFiles : pdfFiles.slice(0, 1));
        }
    }
}
