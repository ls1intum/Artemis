import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faDownload, faFile, faFileArchive, faFileImage, faFilePdf } from '@fortawesome/free-solid-svg-icons';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { FileService } from 'app/shared/http/file.service';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';
import { faSquare, faSquareCheck } from '@fortawesome/free-regular-svg-icons';

@Component({
    selector: 'jhi-attachment-unit',
    templateUrl: './attachment-unit.component.html',
    styleUrls: ['../lecture-unit.component.scss'],
})
export class AttachmentUnitComponent {
    @Input() attachmentUnit: AttachmentUnit;
    @Input() isPresentationMode = false;
    @Output() onCompletion: EventEmitter<LectureUnitCompletionEvent> = new EventEmitter();

    isCollapsed = true;

    // Icons
    faDownload = faDownload;
    faSquare = faSquare;
    faSquareCheck = faSquareCheck;

    constructor(private fileService: FileService) {}

    handleCollapse(event: Event) {
        event.stopPropagation();
        this.isCollapsed = !this.isCollapsed;
    }

    downloadAttachment(event: Event) {
        event.stopPropagation();
        if (this.attachmentUnit?.attachment?.link) {
            this.fileService.downloadFileWithAccessToken(this.attachmentUnit?.attachment?.link);
            this.onCompletion.emit({ lectureUnit: this.attachmentUnit, completed: true });
        }
    }

    handleClick(event: Event, completed: boolean) {
        event.stopPropagation();
        this.onCompletion.emit({ lectureUnit: this.attachmentUnit, completed });
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
