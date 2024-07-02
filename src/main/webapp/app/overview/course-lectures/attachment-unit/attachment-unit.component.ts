import { Component, inject } from '@angular/core';
import { LectureUnitDirective } from 'app/overview/course-lectures/lecture-unit/lecture-unit.directive';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { LectureUnitComponent } from 'app/overview/course-lectures/lecture-unit/lecture-unit.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
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
import { FileService } from 'app/shared/http/file.service';

@Component({
    selector: 'jhi-attachment-unit',
    standalone: true,
    imports: [LectureUnitComponent, ArtemisSharedCommonModule],
    templateUrl: './attachment-unit.component.html',
})
export class AttachmentUnitComponent extends LectureUnitDirective<AttachmentUnit> {
    protected readonly faDownload = faDownload;

    private readonly fileService = inject(FileService);

    /**
     * Returns the name of the attachment file (including its file extension)
     */
    getFileName(): string {
        if (this.lectureUnit().attachment?.link) {
            const link = this.lectureUnit().attachment!.link!;
            return link.substring(link.lastIndexOf('/') + 1);
        }
        return '';
    }

    handleDownload() {
        this.logEvent();

        if (this.lectureUnit().attachment?.link) {
            const link = this.lectureUnit().attachment!.link!;
            this.fileService.downloadFile(link);
            this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: true });
        }
    }

    /**
     * Returns the matching icon for the file extension of the attachment
     */
    getAttachmentIcon(): IconDefinition {
        if (this.lectureUnit().attachment?.link) {
            const fileExtension = this.lectureUnit().attachment?.link?.split('.').pop()!.toLocaleLowerCase();
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
