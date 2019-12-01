import { Component, ElementRef, OnInit, ViewChild, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Lecture } from 'app/entities/lecture';
import { Attachment, AttachmentService, AttachmentType } from 'app/entities/attachment';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import * as moment from 'moment';
import { FileService } from 'app/shared';
import { Subject } from 'rxjs';

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
export class LectureAttachmentsComponent implements OnInit, OnDestroy {
    @ViewChild('fileInput', { static: false }) fileInput: ElementRef;
    lecture: Lecture;
    attachments: Attachment[] = [];
    attachmentToBeCreated: Attachment | null;
    attachmentBackup: Attachment | null;
    attachmentFile: any;
    isUploadingAttachment: boolean;
    isDownloadingAttachmentLink: string | null;
    notificationText: string | null;
    erroredFile: File | null;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        protected activatedRoute: ActivatedRoute,
        private attachmentService: AttachmentService,
        private httpClient: HttpClient,
        private fileUploaderService: FileUploaderService,
        private fileService: FileService,
    ) {}

    ngOnInit() {
        this.notificationText = null;
        this.activatedRoute.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
            this.attachmentService.findAllByLectureId(this.lecture.id).subscribe((attachmentsResponse: HttpResponse<Attachment[]>) => {
                this.attachments = attachmentsResponse.body!;
            });
        });
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
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
            return this.addAttachment();
        }
        this.attachmentToBeCreated!.version++;
        this.attachmentToBeCreated!.uploadDate = moment();

        if (this.attachmentToBeCreated!.id) {
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.attachmentService.update(this.attachmentToBeCreated!, requestOptions).subscribe((attachmentRes: HttpResponse<Attachment>) => {
                this.attachmentToBeCreated = null;
                this.attachmentBackup = null;
                this.notificationText = null;
                this.attachments = this.attachments.map(el => {
                    return el.id === attachmentRes.body!.id ? attachmentRes.body! : el;
                });
            });
        } else {
            this.attachmentService.create(this.attachmentToBeCreated!).subscribe((attachmentRes: HttpResponse<Attachment>) => {
                this.attachments.push(attachmentRes.body!);
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
        this.attachmentService.delete(attachment.id).subscribe(
            () => {
                this.attachments = this.attachments.filter(el => el.id !== attachment.id);
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    cancel() {
        if (this.attachmentBackup) {
            this.resetAttachment();
        }
        this.attachmentToBeCreated = null;
        this.erroredFile = null;
    }

    resetAttachment() {
        if (this.attachmentBackup) {
            this.attachments = this.attachments.map(attachment => {
                if (attachment.id === this.attachmentBackup!.id) {
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
        if (!this.isDownloadingAttachmentLink) {
            this.isDownloadingAttachmentLink = downloadUrl;
            this.fileService.downloadAttachment(downloadUrl);
            this.isDownloadingAttachmentLink = null;
        }
    }

    /**
     * @function setLectureAttachment
     * @param $event {object} Event object which contains the uploaded file
     */
    setLectureAttachment($event: any): void {
        if ($event.target.files.length) {
            this.erroredFile = null;
            const fileList: FileList = $event.target.files;
            const attachmentFile = fileList[0];
            this.attachmentFile = attachmentFile;
            this.attachmentToBeCreated!.link = attachmentFile['name'];
        }
    }

    /**
     * @function uploadLectureAttachmentAndSave
     * @desc Upload the selected file and add it to the attachment
     */
    uploadLectureAttachmentAndSave(): void {
        const file = this.attachmentFile;

        if (!file && this.attachmentToBeCreated!.link) {
            return this.saveAttachment();
        }

        if (!this.attachmentToBeCreated!.name || !file) {
            return;
        }

        this.isUploadingAttachment = true;
        this.erroredFile = null;
        this.fileUploaderService.uploadFile(file, file['name'], { keepFileName: true }).then(
            result => {
                this.attachmentToBeCreated!.link = result.path;
                this.isUploadingAttachment = false;
                this.attachmentFile = null;
                this.saveAttachment();
            },
            error => {
                console.error('Error during file upload in uploadBackground()', error.message);
                this.erroredFile = file;
                this.fileInput.nativeElement.value = '';
                this.attachmentToBeCreated!.link = null;
                this.isUploadingAttachment = false;
                this.attachmentFile = null;
                this.resetAttachment();
            },
        );
    }
}
