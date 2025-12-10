import { Component, ElementRef, output, signal, viewChild } from '@angular/core';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCloudUploadAlt, faFilePdf } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-pdf-drop-zone',
    standalone: true,
    imports: [NgClass, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
    templateUrl: './pdf-drop-zone.component.html',
    styleUrls: ['./pdf-drop-zone.component.scss'],
})
export class PdfDropZoneComponent {
    filesDropped = output<File[]>();

    fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');

    protected readonly faCloudUploadAlt = faCloudUploadAlt;
    protected readonly faFilePdf = faFilePdf;

    isDragOver = signal(false);

    onDragOver(event: DragEvent): void {
        event.preventDefault();
        event.stopPropagation();
        this.isDragOver.set(true);
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

        const files = event.dataTransfer?.files;
        if (files) {
            this.handleFiles(files);
        }
    }

    onFileInputChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (input.files) {
            this.handleFiles(input.files);
            // Reset input so the same file can be selected again
            input.value = '';
        }
    }

    onClick(): void {
        this.fileInput().nativeElement.click();
    }

    private handleFiles(fileList: FileList): void {
        const pdfFiles: File[] = [];

        for (let i = 0; i < fileList.length; i++) {
            const file = fileList[i];
            if (file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')) {
                pdfFiles.push(file);
            }
        }

        if (pdfFiles.length > 0) {
            this.filesDropped.emit(pdfFiles);
        }
    }
}
