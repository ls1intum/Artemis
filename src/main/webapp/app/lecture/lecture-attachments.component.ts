import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Lecture } from 'app/entities/lecture.model';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import dayjs from 'dayjs/esm';
import { Subject } from 'rxjs';
import { FileService } from 'app/shared/http/file.service';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentService } from 'app/lecture/attachment.service';
import { faPaperclip, faPencilAlt, faQuestionCircle, faSpinner, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FILE_EXTENSIONS } from 'app/shared/constants/file-extensions.constants';

@Component({
    selector: 'jhi-lecture-attachments',
    templateUrl: './lecture-attachments.component.html',
    styleUrls: ['./lecture-attachments.component.scss'],
})
export class LectureAttachmentsComponent implements OnInit, OnDestroy {
    @ViewChild('fileInput', { static: false }) fileInput: ElementRef;
    lecture: Lecture;
    attachments: Attachment[] = [];
    attachmentToBeCreated?: Attachment;
    attachmentBackup?: Attachment;
    attachmentFile?: Blob;
    isUploadingAttachment: boolean;
    isDownloadingAttachmentLink?: string;
    notificationText?: string;
    erroredFile?: Blob;
    errorMessage?: string;

    // A human-readable list of allowed file extensions
    readonly allowedFileExtensions = FILE_EXTENSIONS.join(', ');
    // The list of file extensions for the "accept" attribute of the file input field
    readonly acceptedFileExtensionsFileBrowser = FILE_EXTENSIONS.map((ext) => '.' + ext).join(',');

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faSpinner = faSpinner;
    faTimes = faTimes;
    faPencilAlt = faPencilAlt;
    faPaperclip = faPaperclip;
    faQuestionCircle = faQuestionCircle;

    constructor(
        protected activatedRoute: ActivatedRoute,
        private attachmentService: AttachmentService,
        private httpClient: HttpClient,
        private fileUploaderService: FileUploaderService,
        private fileService: FileService,
    ) {}

    ngOnInit() {
        this.notificationText = undefined;
        this.activatedRoute.parent!.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
            this.attachmentService.findAllByLectureId(this.lecture.id!).subscribe((attachmentsResponse: HttpResponse<Attachment[]>) => {
                this.attachments = attachmentsResponse.body!;
            });
        });
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    get isSubmitPossible() {
        return this.attachmentToBeCreated?.name && (this.attachmentFile || this.attachmentToBeCreated?.link);
    }

    addAttachment() {
        const newAttachment = new Attachment();
        newAttachment.lecture = this.lecture;
        newAttachment.attachmentType = AttachmentType.FILE;
        newAttachment.version = 0;
        newAttachment.uploadDate = dayjs();
        this.attachmentToBeCreated = newAttachment;
    }

    saveAttachment() {
        if (!this.attachmentToBeCreated) {
            return this.addAttachment();
        }
        this.attachmentToBeCreated.version!++;
        this.attachmentToBeCreated.uploadDate = dayjs();

        if (this.attachmentToBeCreated.id) {
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.attachmentService.update(this.attachmentToBeCreated, requestOptions).subscribe((attachmentRes: HttpResponse<Attachment>) => {
                this.attachmentToBeCreated = undefined;
                this.attachmentBackup = undefined;
                this.notificationText = undefined;
                this.attachments = this.attachments.map((el) => {
                    return el.id === attachmentRes.body!.id ? attachmentRes.body! : el;
                });
            });
        } else {
            this.attachmentService.create(this.attachmentToBeCreated!).subscribe((attachmentRes: HttpResponse<Attachment>) => {
                this.attachments.push(attachmentRes.body!);
                this.attachmentToBeCreated = undefined;
                this.attachmentBackup = undefined;
            });
        }
    }

    editAttachment(attachment: Attachment) {
        this.attachmentToBeCreated = attachment;
        this.attachmentBackup = Object.assign({}, attachment, {});
    }

    deleteAttachment(attachment: Attachment) {
        this.attachmentService.delete(attachment.id!).subscribe({
            next: () => {
                this.attachments = this.attachments.filter((attachmentEl) => attachmentEl.id !== attachment.id);
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    cancel() {
        if (this.attachmentBackup) {
            this.resetAttachment();
        }
        this.attachmentToBeCreated = undefined;
        this.erroredFile = undefined;
    }

    resetAttachment() {
        if (this.attachmentBackup) {
            this.attachments = this.attachments.map((attachment) => {
                if (attachment.id === this.attachmentBackup!.id) {
                    attachment = this.attachmentBackup as Attachment;
                }
                return attachment;
            });
            this.attachmentBackup = undefined;
        }
    }

    trackId(index: number, item: Attachment) {
        return item.id;
    }

    downloadAttachment(downloadUrl: string) {
        if (!this.isDownloadingAttachmentLink) {
            this.isDownloadingAttachmentLink = downloadUrl;
            this.fileService.downloadFile(downloadUrl);
            this.isDownloadingAttachmentLink = undefined;
        }
    }

    /**
     * @function setLectureAttachment
     * @param event {object} Event object which contains the uploaded file
     */
    setLectureAttachment(event: any): void {
        if (event.target.files.length) {
            this.erroredFile = undefined;
            const fileList: FileList = event.target.files;
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
        this.erroredFile = undefined;
        this.errorMessage = undefined;
        this.fileUploaderService.uploadFile(file, file['name'], { keepFileName: true }).then(
            (result) => {
                this.attachmentToBeCreated!.link = result.path;
                this.isUploadingAttachment = false;
                this.attachmentFile = undefined;
                this.saveAttachment();
            },
            (error) => {
                this.errorMessage = error.message;
                this.erroredFile = file;
                this.fileInput.nativeElement.value = '';
                this.attachmentToBeCreated!.link = undefined;
                this.isUploadingAttachment = false;
                this.attachmentFile = undefined;
                this.resetAttachment();
            },
        );
    }
}
