import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Lecture } from 'app/entities/lecture.model';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import * as moment from 'moment';
import { Subject } from 'rxjs';
import { FileService } from 'app/shared/http/file.service';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentService } from 'app/lecture/attachment.service';

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
    errorMessage: string | null;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        protected activatedRoute: ActivatedRoute,
        private attachmentService: AttachmentService,
        private httpClient: HttpClient,
        private fileUploaderService: FileUploaderService,
        private fileService: FileService,
    ) {}

    /**
     * Retrieve the attachments of the current lecture on component initialization.
     */
    ngOnInit() {
        this.notificationText = null;
        this.activatedRoute.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
            this.attachmentService.findAllByLectureId(this.lecture.id).subscribe((attachmentsResponse: HttpResponse<Attachment[]>) => {
                this.attachments = attachmentsResponse.body!;
            });
        });
    }

    /**
     * Unsubscribe from dialog error source on component destruction.
     */
    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Imitates the browser back button.
     */
    previousState() {
        window.history.back();
    }

    /**
     * Creates a new attachment object. Will be called when the user clicks the submit button of the new attachment form.
     */
    addAttachment() {
        const newAttachment = new Attachment();
        newAttachment.lecture = this.lecture;
        newAttachment.attachmentType = AttachmentType.FILE;
        newAttachment.version = 0;
        newAttachment.uploadDate = moment();
        this.attachmentToBeCreated = newAttachment;
    }

    /**
     * Saves the new attachment if it exists. Otherwise it creates a new attachment object.
     * If the attachment existed before the attachment will be updated via the attachment service.
     * Otherwise it will be created a new attachment via the attachment service.
     */
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
                this.attachments = this.attachments.map((el) => {
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

    // tslint:disable-next-line:completed-docs
    editAttachment(attachment: Attachment) {
        this.attachmentToBeCreated = attachment;
        this.attachmentBackup = Object.assign({}, attachment, {});
    }

    /**
     * Replace backslashes in the attachment path.
     * @param attachmentLink
     */
    attachmentFileName(attachmentLink: string): string {
        return attachmentLink.replace(/\/.*\//, '');
    }

    /**
     * Deletes the given attachment via the attachment service.
     * @param attachment that should be deleted of type {Attachment}
     */
    deleteAttachment(attachment: Attachment) {
        this.attachmentService.delete(attachment.id).subscribe(
            () => {
                this.attachments = this.attachments.filter((el) => el.id !== attachment.id);
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    /**
     * Clean up the component's state when the user cancels the attachment upload.
     */
    cancel() {
        if (this.attachmentBackup) {
            this.resetAttachment();
        }
        this.attachmentToBeCreated = null;
        this.erroredFile = null;
    }

    /**
     * Resets the already added attachment if the user cancels the attachment upload
     * or the creation of a new attachment via the attachment service failed.
     */
    resetAttachment() {
        if (this.attachmentBackup) {
            this.attachments = this.attachments.map((attachment) => {
                if (attachment.id === this.attachmentBackup!.id) {
                    attachment = this.attachmentBackup as Attachment;
                }
                return attachment;
            });
            this.attachmentBackup = null;
        }
    }

    // tslint:disable-next-line:completed-docs
    trackId(index: number, item: Attachment) {
        return item.id;
    }

    /**
     * Downloads an attachment via the file service.
     * @param downloadUrl of the attachment that should be downloaded of type {string}
     */
    downloadAttachment(downloadUrl: string) {
        if (!this.isDownloadingAttachmentLink) {
            this.isDownloadingAttachmentLink = downloadUrl;
            this.fileService.downloadFileWithAccessToken(downloadUrl);
            this.isDownloadingAttachmentLink = null;
        }
    }

    /**
     * Sets the attachment file if the user adds a file via the input for the attachment file.
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
     * Upload the selected file and add it to the attachment.
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
        this.errorMessage = null;
        this.fileUploaderService.uploadFile(file, file['name'], { keepFileName: true }).then(
            (result) => {
                this.attachmentToBeCreated!.link = result.path;
                this.isUploadingAttachment = false;
                this.attachmentFile = null;
                this.saveAttachment();
            },
            (error) => {
                this.errorMessage = error.message;
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
