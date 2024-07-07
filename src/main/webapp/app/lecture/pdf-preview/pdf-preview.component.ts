import { Component, Input, OnInit } from '@angular/core';
import { Attachment } from 'app/entities/attachment.model';
import { ActivatedRoute } from '@angular/router';
import { AttachmentService } from 'app/lecture/attachment.service';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-pdf-preview-component',
    templateUrl: './pdf-preview.component.html',
    styleUrls: ['./pdf-preview.component.scss'],
})
export class PdfPreviewComponent implements OnInit {
    @Input() attachmentId: number;

    attachment: Attachment;
    imageUrls: string[] = [];

    constructor(
        private route: ActivatedRoute,
        private attachmentService: AttachmentService,
    ) {}

    ngOnInit() {
        this.route.params.subscribe((params) => {
            this.attachmentId = +params['attachmentId']; // Make sure this is always defined
            if (this.attachmentId) {
                this.attachmentService.find(this.attachmentId).subscribe((attachmentResponse: HttpResponse<Attachment>) => {
                    this.attachment = attachmentResponse.body!;
                    if (this.attachment && this.attachment.id) {
                        // Check if id is defined
                        this.attachmentService.getPdfImages(this.attachment.id).subscribe(
                            (imageUrls) => {
                                console.log(imageUrls);
                                this.imageUrls = imageUrls;
                            },
                            (error) => {
                                console.error('Failed to load images', error);
                            },
                        );
                    }
                });
            }
        });
    }
}
