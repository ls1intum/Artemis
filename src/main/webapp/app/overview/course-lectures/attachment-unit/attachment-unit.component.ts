import { Component, Input } from '@angular/core';
import { faFile, faFileArchive, faFileImage, faFilePdf } from '@fortawesome/free-solid-svg-icons';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { FileService } from 'app/shared/http/file.service';

@Component({
    selector: 'jhi-attachment-unit',
    templateUrl: './attachment-unit.component.html',
    styleUrls: ['../lecture-unit.component.scss'],
})
export class AttachmentUnitComponent {
    @Input() attachmentUnit: AttachmentUnit;

    isCollapsed = true;

    constructor(private fileService: FileService) {}

    handleCollapse(event: Event) {
        event.stopPropagation();
        this.isCollapsed = !this.isCollapsed;
    }

    downloadAttachment() {
        if (this.attachmentUnit?.attachment?.link) {
            this.fileService.downloadFileWithAccessToken(this.attachmentUnit?.attachment?.link);
        }
    }

    getFileName() {
        if (this.attachmentUnit?.attachment?.link) {
            return this.attachmentUnit?.attachment?.link.substring(this.attachmentUnit?.attachment?.link.lastIndexOf('/') + 1);
        } else {
            return '';
        }
    }

    getAttachmentIcon() {
        if (this.attachmentUnit?.attachment?.link) {
            const fileExtension = this.attachmentUnit?.attachment?.link.split('.').pop()!.toLocaleLowerCase();
            switch (fileExtension) {
                case 'pdf':
                    return faFilePdf;
                case 'png':
                case 'jpg':
                case 'jpeg':
                case 'svg':
                    return faFileImage;
                case 'zip':
                    return faFileArchive;
                default:
                    return faFile;
            }
        }
        return faFile;
    }
}
