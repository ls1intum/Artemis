import { Component, ElementRef, Input, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Lecture } from 'app/entities/lecture.model';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import dayjs from 'dayjs/esm';
import { Subject } from 'rxjs';
import { FileService } from 'app/shared/http/file.service';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentService } from 'app/lecture/attachment.service';
import { faEye, faPaperclip, faPencilAlt, faQuestionCircle, faSpinner, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { UPLOAD_FILE_EXTENSIONS } from 'app/shared/constants/file-extensions.constants';
import { LectureService } from 'app/lecture/lecture.service';

@Component({
    selector: 'jhi-lecture-attachments',
    templateUrl: './lecture-attachments.component.html',
    styleUrls: ['./lecture-attachments.component.scss'],
})
export class LectureAttachmentsComponent implements OnInit, OnDestroy {
    protected activatedRoute = inject(ActivatedRoute);
    private attachmentService = inject(AttachmentService);
    private lectureService = inject(LectureService);
    private httpClient = inject(HttpClient);
    private fileUploaderService = inject(FileUploaderService);
    private fileService = inject(FileService);

    @ViewChild('fileInput', { static: false }) fileInput: ElementRef;
    @Input() lectureId: number | undefined;
    @Input() showHeader = true;

    lecture: Lecture;
    attachments: Attachment[] = [];
    attachmentToBeCreated?: Attachment;
    attachmentBackup?: Attachment;
    attachmentFile?: File;
    isDownloadingAttachmentLink?: string;
    notificationText?: string;
    erroredFile?: File;
    errorMessage?: string;
    viewButtonAvailable: Record<number, boolean> = {};

    // A human-readable list of allowed file extensions
    readonly allowedFileExtensions = UPLOAD_FILE_EXTENSIONS.join(', ');
    // The list of file extensions for the "accept" attribute of the file input field
    readonly acceptedFileExtensionsFileBrowser = UPLOAD_FILE_EXTENSIONS.map((ext) => '.' + ext).join(',');

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faSpinner = faSpinner;
    faTimes = faTimes;
    faTrash = faTrash;
    faPencilAlt = faPencilAlt;
    faPaperclip = faPaperclip;
    faQuestionCircle = faQuestionCircle;
    faEye = faEye;

    ngOnInit() {
        this.notificationText = undefined;
        this.activatedRoute.parent!.data.subscribe(({ lecture }) => {
            if (this.lectureId) {
                this.lectureService.findWithDetails(this.lectureId).subscribe((lectureResponse: HttpResponse<Lecture>) => {
                    this.lecture = lectureResponse.body!;
                    this.loadAttachments();
                });
            } else {
                this.lecture = lecture;
                this.loadAttachments();
            }
        });
    }

    loadAttachments(): void {
        this.attachmentService.findAllByLectureId(this.lecture.id!).subscribe((attachmentsResponse: HttpResponse<Attachment[]>) => {
            this.attachments = attachmentsResponse.body!;
            this.attachments.forEach((attachment) => {
                this.viewButtonAvailable[attachment.id!] = this.isViewButtonAvailable(attachment.link!);
            });
        });
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    isViewButtonAvailable(attachmentLink: string): boolean {
        return attachmentLink.endsWith('.pdf') ?? false;
    }

    get isSubmitPossible(): boolean {
        return !!(this.attachmentToBeCreated?.name && (this.attachmentFile || this.attachmentToBeCreated?.link));
    }

    addAttachment(): void {
        const newAttachment = new Attachment();
        newAttachment.lecture = this.lecture;
        newAttachment.attachmentType = AttachmentType.FILE;
        newAttachment.version = 0;
        newAttachment.uploadDate = dayjs();
        this.attachmentToBeCreated = newAttachment;
    }

    /**
     * If there is an attachment to save, it will be created or updated depending on its current state. The file will be automatically provided with the request.
     */
    saveAttachment(): void {
        if (!this.attachmentToBeCreated) {
            return;
        }
        this.attachmentToBeCreated.version!++;
        this.attachmentToBeCreated.uploadDate = dayjs();

        if (!this.attachmentFile && !this.attachmentToBeCreated.id) {
            return;
        }

        if (this.attachmentToBeCreated.id) {
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.attachmentService.update(this.attachmentToBeCreated.id, this.attachmentToBeCreated, this.attachmentFile, requestOptions).subscribe({
                next: (attachmentRes: HttpResponse<Attachment>) => {
                    this.attachmentFile = undefined;
                    this.attachmentToBeCreated = undefined;
                    this.attachmentBackup = undefined;
                    this.notificationText = undefined;
                    this.attachments = this.attachments.map((el) => {
                        return el.id === attachmentRes.body!.id ? attachmentRes.body! : el;
                    });
                },
                error: (error: HttpErrorResponse) => this.handleFailedUpload(error),
            });
        } else {
            this.attachmentService.create(this.attachmentToBeCreated!, this.attachmentFile!).subscribe({
                next: (attachmentRes: HttpResponse<Attachment>) => {
                    this.attachments.push(attachmentRes.body!);
                    this.lectureService.findWithDetails(this.lecture.id!).subscribe((lectureResponse: HttpResponse<Lecture>) => {
                        this.lecture = lectureResponse.body!;
                    });
                    this.attachmentFile = undefined;
                    this.attachmentToBeCreated = undefined;
                    this.attachmentBackup = undefined;
                    this.loadAttachments();
                },
                error: (error: HttpErrorResponse) => this.handleFailedUpload(error),
            });
        }
    }

    private handleFailedUpload(error: HttpErrorResponse): void {
        this.errorMessage = error.message;
        this.erroredFile = this.attachmentFile;
        this.fileInput.nativeElement.value = '';
        this.attachmentFile = undefined;
        this.resetAttachment();
    }

    editAttachment(attachment: Attachment): void {
        this.attachmentToBeCreated = attachment;
        this.attachmentBackup = Object.assign({}, attachment, {});
    }

    deleteAttachment(attachment: Attachment): void {
        this.attachmentService.delete(attachment.id!).subscribe({
            next: () => {
                this.attachments = this.attachments.filter((attachmentEl) => attachmentEl.id !== attachment.id);
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    cancel(): void {
        if (this.attachmentBackup) {
            this.resetAttachment();
        }
        this.attachmentToBeCreated = undefined;
        this.erroredFile = undefined;
    }

    resetAttachment(): void {
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

    trackId(index: number, item: Attachment): number | undefined {
        return item.id;
    }

    downloadAttachment(downloadUrl: string): void {
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
    setLectureAttachment(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (!input.files?.length) {
            return;
        }
        const attachmentFile = input.files[0];
        this.attachmentFile = attachmentFile;
        this.attachmentToBeCreated!.link = attachmentFile.name;
        // automatically set the name in case it is not yet specified
        if (this.attachmentToBeCreated!.name == undefined || this.attachmentToBeCreated!.name == '') {
            this.attachmentToBeCreated!.name = this.attachmentFile.name.replace(/\.[^/.]+$/, '');
        }
    }
}
