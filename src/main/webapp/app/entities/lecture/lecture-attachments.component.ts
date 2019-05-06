import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpClient } from '@angular/common/http';
import { Lecture } from 'app/entities/lecture';
import { Attachment, AttachmentService, AttachmentType } from 'app/entities/attachment';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import * as moment from 'moment';

@Component({
    selector: 'jhi-lecture-attachments',
    templateUrl: './lecture-attachments.component.html',
    styles: [
            `
            .edit-overlay {
                position: absolute;
                left: 0;
                right: 0;
                display: flex;
                justify-content: center;
                background-color: rgba(255, 255, 255, 0.9);
                z-index: 9;
                font-size: 18px;
            }
        `,
    ],
})
export class LectureAttachmentsComponent implements OnInit {
    lecture: Lecture;
    attachments: Attachment[] = [];
    attachmentToBeCreated: Attachment;
    attachmentBackup: Attachment;
    attachmentFile: any;
    isUploadingAttachment: boolean;
    isDownloadingAttachment = false;
    notificationText: string;

    constructor(
        protected activatedRoute: ActivatedRoute,
        private attachmentService: AttachmentService,
        private httpClient: HttpClient,
        private fileUploaderService: FileUploaderService,
    ) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
            this.attachmentService.findAllByLectureId(this.lecture.id).subscribe((attachmentsResponse: HttpResponse<Attachment[]>) => {
                this.attachments = attachmentsResponse.body;
            });
        });
    }

    previousState() {
        window.history.back();
    }

    addAttachment() {
        const newAttachment = new Attachment();
        newAttachment.lecture = this.lecture;
        newAttachment.attachmentType = AttachmentType.FILE;
        newAttachment.version = 0;
        newAttachment.uploadDate = moment();
        this.attachmentToBeCreated = newAttachment;
    }

    saveAttachment() {
        if (!this.attachmentToBeCreated) {
            this.attachmentToBeCreated.version = 0;
        }
        this.attachmentToBeCreated.version++;
        this.attachmentToBeCreated.uploadDate = moment();

        if (this.attachmentToBeCreated.id) {
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.attachmentService.update(this.attachmentToBeCreated, requestOptions).subscribe((attachmentRes: HttpResponse<Attachment>) => {
                this.attachmentToBeCreated = null;
                this.attachmentBackup = null;
                this.attachments = this.attachments.map(el => {
                    return el.id === attachmentRes.body.id ? attachmentRes.body : el;
                });
            });
        } else {
            this.attachmentService.create(this.attachmentToBeCreated).subscribe((attachmentRes: HttpResponse<Attachment>) => {
                this.attachments.push(attachmentRes.body);
                this.attachmentToBeCreated = null;
                this.attachmentBackup = null;
            });
        }
    }

    editAttachment(attachment: Attachment) {
        this.attachmentToBeCreated = attachment;
        this.attachmentBackup = Object.assign({}, attachment, {});
    }

    attachmentFileName(attachmentLink: string): string {
        return attachmentLink.replace(/\/.*\//, '');
    }

    deleteAttachment(attachment: Attachment) {
        this.attachmentService.delete(attachment.id).subscribe(() => {
            this.attachments = this.attachments.filter(el => el.id !== attachment.id);
        });
    }

    cancel() {
        if (this.attachmentBackup) {
            this.resetAttachment();
        }
        this.attachmentToBeCreated = null;
    }

    resetAttachment() {
        if (this.attachmentBackup) {
            this.attachments = this.attachments.map(attachment => {
                if (attachment.id === this.attachmentBackup.id) {
                    attachment = this.attachmentBackup as Attachment;
                }
                return attachment;
            });
            this.attachmentBackup = null;
        }
    }

    trackId(index: number, item: Attachment) {
        return item.id;
    }

    downloadAttachment(downloadUrl: string) {
        this.isDownloadingAttachment = true;
        this.httpClient.get(downloadUrl, { observe: 'response', responseType: 'blob' }).subscribe(
            response => {
                const blob = new Blob([response.body], { type: response.headers.get('content-type') });
                const url = window.URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.setAttribute('href', url);
                link.setAttribute('download', response.headers.get('filename'));
                document.body.appendChild(link); // Required for FF
                link.click();
                window.URL.revokeObjectURL(url);
                this.isDownloadingAttachment = false;
            },
            error => {
                this.isDownloadingAttachment = false;
            },
        );
    }

    /**
     * @function setLectureAttachment
     * @param $event {object} Event object which contains the uploaded file
     */
    setLectureAttachment($event: any): void {
        if ($event.target.files.length) {
            const fileList: FileList = $event.target.files;
            const attachmentFile = fileList[0];
            this.attachmentFile = attachmentFile;
            this.attachmentToBeCreated.link = attachmentFile['name'];
        }
    }

    /**
     * @function uploadLectureAttachmentAndSave
     * @desc Upload the selected file and add it to the attachment
     */
    uploadLectureAttachmentAndSave(): void {
        const file = this.attachmentFile;

        if (!file && this.attachmentToBeCreated.link) {
            return this.saveAttachment();
        }

        if (!this.attachmentToBeCreated.name || !file) {
            return;
        }

        this.isUploadingAttachment = true;
        this.fileUploaderService.uploadFile(file, file['name'], { keepFileName: true }).then(
            result => {
                this.attachmentToBeCreated.link = result.path;
                this.isUploadingAttachment = false;
                this.attachmentFile = null;
                this.saveAttachment();
            },
            error => {
                console.error('Error during file upload in uploadBackground()', error.message);
                this.isUploadingAttachment = false;
                this.attachmentFile = null;
                this.resetAttachment();
            },
        );
    }
}
