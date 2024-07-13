import { Component, ElementRef, Input, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AttachmentService } from 'app/lecture/attachment.service';
import * as PDFJS from 'pdfjs-dist';
import 'pdfjs-dist/build/pdf.worker.mjs';

@Component({
    selector: 'jhi-pdf-preview-component',
    templateUrl: './pdf-preview.component.html',
    styleUrls: ['./pdf-preview.component.scss'],
})
export class PdfPreviewComponent implements OnInit {
    @Input() attachmentId: number;
    @ViewChild('pdfContainer', { static: true }) pdfContainer: ElementRef;

    constructor(
        private route: ActivatedRoute,
        private attachmentService: AttachmentService,
    ) {}

    ngOnInit() {
        this.route.params.subscribe((params) => {
            const attachmentId = +params['attachmentId'];
            if (attachmentId) {
                this.attachmentService.getAttachmentFile(attachmentId).subscribe(
                    (blob: Blob) => {
                        const fileURL = URL.createObjectURL(blob);
                        this.loadPdf(fileURL);
                    },
                    (error) => console.error('Failed to load PDF file', error),
                );
            }
        });
    }

    private loadPdf(fileUrl: string) {
        const loadingTask = PDFJS.getDocument(fileUrl);
        loadingTask.promise.then(
            (pdf: { numPages: number; getPage: (arg0: number) => Promise<any> }) => {
                for (let i = 1; i <= pdf.numPages; i++) {
                    pdf.getPage(i).then((page) => {
                        const viewport = page.getViewport({ scale: 0.5 });
                        const canvas = document.createElement('canvas');
                        const context = canvas.getContext('2d');

                        const renderTask = page.render({
                            canvasContext: context,
                            viewport: viewport,
                        });
                        renderTask.promise.then(() => {
                            this.pdfContainer.nativeElement.appendChild(canvas);
                            URL.revokeObjectURL(fileUrl);
                        });
                    });
                }
            },
            (error: any) => {
                console.error('Error loading PDF: ', error);
            },
        );
    }
}
