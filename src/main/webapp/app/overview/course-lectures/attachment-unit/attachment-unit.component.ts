import { Component, Input, OnInit } from '@angular/core';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { FileService } from 'app/shared/http/file.service';

@Component({
    selector: 'jhi-attachment-unit',
    templateUrl: './attachment-unit.component.html',
    styleUrls: ['../../course-exercises/course-exercise-row.scss'],
})
export class AttachmentUnitComponent implements OnInit {
    @Input()
    attachmentUnit: AttachmentUnit;

    @Input()
    isPresentationMode: false;

    constructor(private fileService: FileService) {}

    ngOnInit(): void {}

    downloadAttachment() {
        if (this.attachmentUnit?.attachment?.link) {
            this.fileService.downloadFileWithAccessToken(this.attachmentUnit?.attachment?.link);
        }
    }

    getAttachmentIcon() {
        if (this.attachmentUnit?.attachment?.link) {
            const fileExtension = this.attachmentUnit?.attachment?.link.split('.').pop()!.toLocaleLowerCase();
            switch (fileExtension) {
                case 'pdf':
                    return 'file-pdf';
                case 'png':
                case 'jpg':
                case 'jpeg':
                case 'svg':
                    return 'file-image';
                case 'zip':
                    return 'file-archive';
                default:
                    return 'file';
            }
        }
    }
}
