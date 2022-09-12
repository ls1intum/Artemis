import { Component, EventEmitter, Input, Output } from '@angular/core';
import {
    faDownload,
    faFile,
    faFileArchive,
    faFileCode,
    faFileCsv,
    faFileExcel,
    faFileImage,
    faFileLines,
    faFilePdf,
    faFilePen,
    faFilePowerpoint,
    faFileWord,
} from '@fortawesome/free-solid-svg-icons';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { FileService } from 'app/shared/http/file.service';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';
import { faSquare, faSquareCheck } from '@fortawesome/free-regular-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';

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

    /**
     * Returns the name of the attachment file (including its file extension)
     */
    getFileName(): string {
        if (this.attachmentUnit?.attachment?.link) {
            return this.attachmentUnit?.attachment?.link.substring(this.attachmentUnit?.attachment?.link.lastIndexOf('/') + 1);
        } else {
            return '';
        }
    }

    /**
     * Returns the matching icon for the file extension of the attachment
     */
    getAttachmentIcon(): IconDefinition {
        if (this.attachmentUnit?.attachment?.link) {
            const fileExtension = this.attachmentUnit?.attachment?.link.split('.').pop()!.toLocaleLowerCase();
            switch (fileExtension) {
                case 'png':
                case 'jpg':
                case 'jpeg':
                case 'gif':
                case 'svg':
                    return faFileImage;
                case 'pdf':
                    return faFilePdf;
                case 'zip':
                case 'tar':
                    return faFileArchive;
                case 'txt':
                case 'rtf':
                case 'md':
                    return faFileLines;
                case 'htm':
                case 'html':
                case 'json':
                    return faFileCode;
                case 'doc':
                case 'docx':
                case 'pages':
                case 'pages-tef':
                case 'odt':
                    return faFileWord;
                case 'csv':
                    return faFileCsv;
                case 'xls':
                case 'xlsx':
                case 'numbers':
                case 'ods':
                    return faFileExcel;
                case 'ppt':
                case 'pptx':
                case 'key':
                case 'odp':
                    return faFilePowerpoint;
                case 'odg':
                case 'odc':
                case 'odi':
                case 'odf':
                    return faFilePen;
                default:
                    return faFile;
            }
        }
        return faFile;
    }
}
