import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AttachmentService } from 'app/lecture/attachment.service';
import * as PDFJS from 'pdfjs-dist';
import { Attachment } from 'app/entities/attachment.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-pdf-preview-component',
    templateUrl: './pdf-preview.component.html',
    styleUrls: ['./pdf-preview.component.scss'],
})
export class PdfPreviewComponent implements OnInit, OnDestroy {
    @ViewChild('pdfContainer', { static: true }) pdfContainer: ElementRef;
    @ViewChild('enlargedCanvas') enlargedCanvas: ElementRef;
    attachment?: Attachment;
    attachmentUnit?: AttachmentUnit;
    isEnlargedView: boolean = false;
    currentPage: number = 1;
    totalPages: number = 0;

    constructor(
        private route: ActivatedRoute,
        private attachmentService: AttachmentService,
        private attachmentUnitService: AttachmentUnitService,
        private alertService: AlertService,
    ) {
        PDFJS.GlobalWorkerOptions.workerSrc = './pdfjs/pdf.worker.min.mjs';
    }

    ngOnInit() {
        this.route.data.subscribe((data) => {
            if ('attachment' in data) {
                this.attachment = data.attachment;
                if (this.attachment?.id) {
                    this.attachmentService.getAttachmentFile(this.attachment.id).subscribe({
                        next: (blob: Blob) => this.loadPdf(URL.createObjectURL(blob)),
                        error: (error) => onError(this.alertService, error),
                    });
                } else {
                    this.alertService.error('artemisApp.attachment.pdfPreview.attachmentIDError');
                }
            } else if ('attachmentUnit' in data) {
                this.attachmentUnit = data.attachmentUnit;
                if (this.attachmentUnit?.id) {
                    this.attachmentUnitService.getAttachmentFile(this.attachmentUnit.id).subscribe({
                        next: (blob: Blob) => this.loadPdf(URL.createObjectURL(blob)),
                        error: (error) => onError(this.alertService, error),
                    });
                } else {
                    this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUnitIDError');
                }
            }
        });
        document.addEventListener('keydown', this.handleKeyboardEvents);
    }

    ngOnDestroy() {
        document.removeEventListener('keydown', this.handleKeyboardEvents);
    }

    private async loadPdf(fileUrl: string) {
        try {
            const loadingTask = PDFJS.getDocument(fileUrl);
            const pdf = await loadingTask.promise;
            this.totalPages = pdf.numPages;

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
            onError(this.alertService, error);
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
        container.style.cssText = `position: relative; display: inline-block; width: ${canvas.style.width}; height: ${canvas.style.height}; margin: 20px; box-shadow: 0 2px 6px var(--pdf-preview-canvas-shadow);`;

        const overlay = this.createOverlay(pageIndex);
        container.appendChild(canvas);
        container.appendChild(overlay);

        container.addEventListener('mouseenter', () => (overlay.style.opacity = '1'));
        container.addEventListener('mouseleave', () => (overlay.style.opacity = '0'));
        overlay.addEventListener('click', () => this.displayEnlargedCanvas(canvas, pageIndex));

        return container;
    }

    private createOverlay(pageIndex: number): HTMLDivElement {
        const overlay = document.createElement('div');
        overlay.classList.add('pdf-page-overlay');
        overlay.innerHTML = `<span>${pageIndex}</span>`;
        overlay.style.cssText = `position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: flex; justify-content: center; align-items: center; font-size: 24px; color: white; background-color: rgba(0, 0, 0, 0.4); z-index: 1; transition: opacity 0.3s ease; opacity: 0; cursor: pointer;`;
        return overlay;
    }

    displayEnlargedCanvas(originalCanvas: HTMLCanvasElement, pageIndex: number) {
        this.isEnlargedView = true;
        this.currentPage = pageIndex;
        this.updateEnlargedCanvas(originalCanvas);
        this.toggleBodyScroll(true);
    }

    private updateEnlargedCanvas(originalCanvas: HTMLCanvasElement) {
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

    handleKeyboardEvents = (event: KeyboardEvent) => {
        if (this.isEnlargedView) {
            if (event.key === 'ArrowRight' && this.currentPage < this.totalPages) {
                this.navigatePages('next');
            } else if (event.key === 'ArrowLeft' && this.currentPage > 1) {
                this.navigatePages('prev');
            }
        }
    };

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

    navigatePages(direction: string) {
        const nextPageIndex = direction === 'next' ? this.currentPage + 1 : this.currentPage - 1;

        if (nextPageIndex > 0 && nextPageIndex <= this.totalPages) {
            this.currentPage = nextPageIndex;
            const canvas = this.pdfContainer.nativeElement.querySelectorAll('.pdf-page-container canvas')[this.currentPage - 1];
            this.updateEnlargedCanvas(canvas);
        }
    }
}
