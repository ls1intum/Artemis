import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AttachmentService } from 'app/lecture/attachment.service';
import * as PDFJS from 'pdfjs-dist';
import 'pdfjs-dist/build/pdf.worker';
import { Attachment } from 'app/entities/attachment.model';

@Component({
    selector: 'jhi-pdf-preview-component',
    templateUrl: './pdf-preview.component.html',
    styleUrls: ['./pdf-preview.component.scss'],
})
export class PdfPreviewComponent implements OnInit {
    attachment: Attachment;
    @ViewChild('pdfContainer', { static: true }) pdfContainer: ElementRef;
    @ViewChild('enlargedCanvas') enlargedCanvas: ElementRef;
    isEnlargedView: boolean = false;

    constructor(
        private route: ActivatedRoute,
        private attachmentService: AttachmentService,
    ) {}

    ngOnInit() {
        this.route.data.subscribe((data: { attachment: Attachment }) => {
            this.attachment = data.attachment;
            if (this.attachment && this.attachment.id) {
                this.attachmentService.getAttachmentFile(this.attachment.id).subscribe({
                    next: (blob: Blob) => this.loadPdf(URL.createObjectURL(blob)),
                    error: (error) => console.error('Failed to load PDF file', error),
                });
            } else {
                console.error('Invalid attachment or attachment ID.');
            }
        });
    }

    private async loadPdf(fileUrl: string) {
        try {
            const loadingTask = PDFJS.getDocument(fileUrl);
            const pdf = await loadingTask.promise;

            for (let i = 1; i <= pdf.numPages; i++) {
                const page = await pdf.getPage(i);
                const viewport = page.getViewport({ scale: 1 });
                const canvas = this.createCanvas(viewport);
                const context = canvas.getContext('2d');
                if (context) {
                    await page.render({ canvasContext: context, viewport }).promise;
                }

                const container = this.createContainer(canvas, i);
                this.pdfContainer.nativeElement.appendChild(container);
            }

            URL.revokeObjectURL(fileUrl);
        } catch (error) {
            console.error('Error loading PDF:', error);
        }
    }

    private createCanvas(viewport: PDFJS.PageViewport): HTMLCanvasElement {
        const canvas = document.createElement('canvas');
        canvas.height = viewport.height;
        canvas.width = viewport.width;
        const fixedWidth = 250;
        const scaleFactor = fixedWidth / viewport.width;
        canvas.style.width = `${fixedWidth}px`;
        canvas.style.height = `${viewport.height * scaleFactor}px`;
        return canvas;
    }

    private createContainer(canvas: HTMLCanvasElement, pageIndex: number): HTMLDivElement {
        const container = document.createElement('div');
        container.classList.add('pdf-page-container');
        container.style.cssText = `position: relative; display: inline-block; width: ${canvas.style.width}; height: ${canvas.style.height}; margin: 20px; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);`;

        const overlay = this.createOverlay(pageIndex);
        container.appendChild(canvas);
        container.appendChild(overlay);

        container.addEventListener('mouseenter', () => (overlay.style.opacity = '1'));
        container.addEventListener('mouseleave', () => (overlay.style.opacity = '0'));
        overlay.addEventListener('click', () => this.displayEnlargedCanvas(canvas));

        return container;
    }

    private createOverlay(pageIndex: number): HTMLDivElement {
        const overlay = document.createElement('div');
        overlay.classList.add('pdf-page-overlay');
        overlay.innerHTML = `<span>${pageIndex}</span>`;
        overlay.style.cssText = `position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: flex; justify-content: center; align-items: center; font-size: 24px; color: white; background-color: rgba(0, 0, 0, 0.4); z-index: 1; transition: opacity 0.3s ease; opacity: 0; cursor: pointer;`;
        return overlay;
    }

    private displayEnlargedCanvas(originalCanvas: HTMLCanvasElement) {
        this.isEnlargedView = true;
        this.toggleBodyScroll(true);

        setTimeout(() => {
            if (this.isEnlargedView) {
                const enlargedCanvas = this.enlargedCanvas.nativeElement;
                const context = enlargedCanvas.getContext('2d');
                const containerWidth = this.pdfContainer.nativeElement.clientWidth;
                const containerHeight = this.pdfContainer.nativeElement.clientHeight;

                const scaleFactor = Math.min(1, containerWidth / originalCanvas.width, containerHeight / originalCanvas.height);
                enlargedCanvas.width = originalCanvas.width * scaleFactor;
                enlargedCanvas.height = originalCanvas.height * scaleFactor;

                context.clearRect(0, 0, enlargedCanvas.width, enlargedCanvas.height);
                context.drawImage(originalCanvas, 0, 0, enlargedCanvas.width, enlargedCanvas.height);

                enlargedCanvas.parentElement.style.top = `${this.pdfContainer.nativeElement.scrollTop}px`;
            }
        }, 50);
    }

    closeEnlargedView() {
        this.isEnlargedView = false;
        this.toggleBodyScroll(false);
    }

    toggleBodyScroll(disable: boolean): void {
        this.pdfContainer.nativeElement.style.overflow = disable ? 'hidden' : 'auto';
    }

    closeIfOutside(event: MouseEvent): void {
        const target = event.target as HTMLElement;
        const enlargedCanvas = this.enlargedCanvas.nativeElement;

        if (target.classList.contains('enlarged-container') && target !== enlargedCanvas) {
            this.closeEnlargedView();
        }
    }
}
