import { Component, ElementRef, Input, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AttachmentService } from 'app/lecture/attachment.service';
import * as PDFJS from 'pdfjs-dist';
import 'pdfjs-dist/build/pdf.worker';

@Component({
    selector: 'jhi-pdf-preview-component',
    templateUrl: './pdf-preview.component.html',
    styleUrls: ['./pdf-preview.component.scss'],
})
export class PdfPreviewComponent implements OnInit {
    @Input() attachmentId: number;
    @ViewChild('pdfContainer', { static: true }) pdfContainer: ElementRef;
    @ViewChild('enlargedCanvas') enlargedCanvas: ElementRef;
    isEnlargedView: boolean = false;

    constructor(
        private route: ActivatedRoute,
        private attachmentService: AttachmentService,
    ) {}

    ngOnInit() {
        this.route.params.subscribe((params) => {
            const attachmentId = +params['attachmentId'];
            if (attachmentId) {
                this.attachmentService.getAttachmentFile(attachmentId).subscribe({
                    next: (blob: Blob) => {
                        const fileURL = URL.createObjectURL(blob);
                        this.loadPdf(fileURL);
                    },
                    error: (error) => {
                        console.error('Failed to load PDF file', error);
                    },
                });
            }
        });
    }

    private async loadPdf(fileUrl: string) {
        try {
            const loadingTask = PDFJS.getDocument(fileUrl);
            const pdf = await loadingTask.promise;
            const numPages = pdf.numPages;

            for (let i = 1; i <= numPages; i++) {
                const page = await pdf.getPage(i);
                const viewport = page.getViewport({ scale: 1 });
                const canvas = document.createElement('canvas');
                const context = canvas.getContext('2d');
                if (context) {
                    canvas.height = viewport.height;
                    canvas.width = viewport.width;

                    await page.render({
                        canvasContext: context,
                        viewport: viewport,
                    }).promise;

                    const fixedWidth = 250;
                    const scaleFactor = fixedWidth / viewport.width;
                    const fixedHeight = viewport.height * scaleFactor;

                    canvas.style.width = `${fixedWidth}px`;
                    canvas.style.height = `${fixedHeight}px`;

                    const container = document.createElement('div');
                    container.classList.add('pdf-page-container');
                    container.style.position = 'relative';
                    container.style.display = 'inline-block';
                    container.style.width = `${fixedWidth}px`;
                    container.style.height = `${fixedHeight}px`;
                    container.style.margin = '20px'; // Margin for the container, not the canvas
                    container.style.boxShadow = '0 2px 6px rgba(0, 0, 0, 0.1)';

                    const overlay = document.createElement('div');
                    overlay.classList.add('pdf-page-overlay');
                    overlay.innerHTML = `<span>${i}</span>`;
                    overlay.style.position = 'absolute';
                    overlay.style.top = '0';
                    overlay.style.left = '0';
                    overlay.style.width = '100%';
                    overlay.style.height = '100%';
                    overlay.style.display = 'flex';
                    overlay.style.justifyContent = 'center';
                    overlay.style.alignItems = 'center';
                    overlay.style.fontSize = '24px';
                    overlay.style.color = 'white';
                    overlay.style.backgroundColor = 'rgba(0, 0, 0, 0.4)';
                    overlay.style.zIndex = '1';
                    overlay.style.transition = 'opacity 0.3s ease';
                    overlay.style.opacity = '0';
                    overlay.style.cursor = 'pointer';

                    container.appendChild(canvas);
                    container.appendChild(overlay);

                    this.pdfContainer.nativeElement.appendChild(container);

                    container.addEventListener('mouseenter', () => {
                        overlay.style.opacity = '1';
                    });
                    container.addEventListener('mouseleave', () => {
                        overlay.style.opacity = '0';
                    });

                    overlay.addEventListener('click', () => {
                        this.displayEnlargedCanvas(canvas);
                    });
                }
            }

            URL.revokeObjectURL(fileUrl);
        } catch (error) {
            console.error('Error loading PDF:', error);
        }
    }

    private displayEnlargedCanvas(originalCanvas: HTMLCanvasElement) {
        this.isEnlargedView = true;
        this.toggleBodyScroll(true); // Optional: Disable scrolling when enlarged view is active

        setTimeout(() => {
            if (this.isEnlargedView) {
                const enlargedCanvas = this.enlargedCanvas.nativeElement;
                const context = enlargedCanvas.getContext('2d');
                const containerWidth = this.pdfContainer.nativeElement.clientWidth;
                const containerHeight = this.pdfContainer.nativeElement.clientHeight;
                const scrollOffset = this.pdfContainer.nativeElement.scrollTop;

                // Calculate scale factor based on the container size and original canvas size
                const widthScale = containerWidth / originalCanvas.width;
                const heightScale = containerHeight / originalCanvas.height;
                const scaleFactor = Math.min(1, widthScale, heightScale); // Ensures that the canvas does not exceed the container

                enlargedCanvas.width = originalCanvas.width * scaleFactor;
                enlargedCanvas.height = originalCanvas.height * scaleFactor;

                context.clearRect(0, 0, enlargedCanvas.width, enlargedCanvas.height);
                context.drawImage(originalCanvas, 0, 0, enlargedCanvas.width, enlargedCanvas.height);

                // Set the top position based on the current scroll position
                this.enlargedCanvas.nativeElement.parentElement.style.top = `${scrollOffset}px`;
            }
        }, 50);
    }

    closeEnlargedView() {
        this.isEnlargedView = false;
        this.toggleBodyScroll(false);
    }

    toggleBodyScroll(disable: boolean): void {
        const pdfContainerElement = this.pdfContainer.nativeElement;
        if (disable) {
            pdfContainerElement.style.overflow = 'hidden';
        } else {
            pdfContainerElement.style.overflow = 'auto';
        }
    }
}
