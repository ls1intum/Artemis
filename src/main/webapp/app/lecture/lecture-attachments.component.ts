import { Component, ElementRef, OnDestroy, ViewChild, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Lecture } from 'app/entities/lecture.model';
import dayjs from 'dayjs/esm';
import { Subject, Subscription } from 'rxjs';
import { FileService } from 'app/shared/http/file.service';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentService } from 'app/lecture/attachment.service';
import { faEye, faPaperclip, faPencilAlt, faQuestionCircle, faSpinner, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER, ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE } from 'app/shared/constants/file-extensions.constants';
import { LectureService } from 'app/lecture/lecture.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';

export interface LectureAttachmentFormData {
    attachmentName?: string;
    attachmentFileName?: string;
    releaseDate?: dayjs.Dayjs;
    notificationText?: string;
}

@Component({
    selector: 'jhi-lecture-attachments',
    templateUrl: './lecture-attachments.component.html',
    styleUrls: ['./lecture-attachments.component.scss'],
})
export class LectureAttachmentsComponent implements OnDestroy {
    protected readonly faSpinner = faSpinner;
    protected readonly faTimes = faTimes;
    protected readonly faTrash = faTrash;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faPaperclip = faPaperclip;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faEye = faEye;

    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;

    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly attachmentService = inject(AttachmentService);
    private readonly lectureService = inject(LectureService);
    private readonly fileService = inject(FileService);
    private readonly formBuilder = inject(FormBuilder);

    @ViewChild('fileInput', { static: false }) fileInput: ElementRef;
    datePickerComponent = viewChild(FormDateTimePickerComponent);

    lectureId = input<number>();
    showHeader = input<boolean>(true);
    redirectToLecturePage = input<boolean>(true);

    lecture = signal<Lecture>(new Lecture());
    attachments: Attachment[] = [];
    attachmentToBeUpdatedOrCreated = signal<Attachment | undefined>(undefined);
    attachmentBackup?: Attachment;
    attachmentFile = signal<File | undefined>(undefined);
    isDownloadingAttachmentLink?: string;
    notificationText?: string;
    erroredFile?: File;
    errorMessage?: string;
    viewButtonAvailable: Record<number, boolean> = {};

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    private routeDataSubscription?: Subscription;

    form: FormGroup = this.formBuilder.group({
        attachmentName: [undefined as string | undefined, [Validators.required]],
        attachmentFileName: [undefined as string | undefined],
        releaseDate: [undefined as dayjs.Dayjs | undefined],
        notificationText: [undefined as string | undefined],
    });

    isFileSelectionValid = computed(() => {
        return this.attachmentFile() || this.attachmentToBeUpdatedOrCreated()?.link;
    });

    private readonly statusChanges = toSignal(this.form.statusChanges ?? 'INVALID');
    isFormValid = computed(() => this.statusChanges() === 'VALID' && this.isFileSelectionValid() && this.datePickerComponent()?.isValid());

    constructor() {
        effect(
            () => {
                this.notificationText = undefined;
                this.routeDataSubscription?.unsubscribe(); // in case the subscription was already defined
                this.routeDataSubscription = this.activatedRoute.parent!.data.subscribe(({ lecture }) => {
                    if (this.lectureId()) {
                        this.lectureService.findWithDetails(this.lectureId()!).subscribe((lectureResponse: HttpResponse<Lecture>) => {
                            this.lecture.set(lectureResponse.body!);
                            this.loadAttachments();
                        });
                    } else {
                        this.lecture.set(lecture);
                        this.loadAttachments();
                    }
                });
            },
            { allowSignalWrites: true },
        );
    }

    loadAttachments(): void {
        this.attachmentService.findAllByLectureId(this.lecture().id!).subscribe((attachmentsResponse: HttpResponse<Attachment[]>) => {
            this.attachments = attachmentsResponse.body!;
            this.attachments.forEach((attachment) => {
                this.viewButtonAvailable[attachment.id!] = this.isViewButtonAvailable(attachment.link!);
            });
        });
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
        this.routeDataSubscription?.unsubscribe();
    }

    isViewButtonAvailable(attachmentLink: string): boolean {
        return attachmentLink.endsWith('.pdf') ?? false;
    }

    addAttachment(): void {
        const newAttachment = new Attachment();
        newAttachment.lecture = this.lecture();
        newAttachment.attachmentType = AttachmentType.FILE;
        newAttachment.version = 0;
        newAttachment.uploadDate = dayjs();
        this.attachmentToBeUpdatedOrCreated.set(newAttachment);
    }

    /**
     * If there is an attachment to save, it will be created or updated depending on its current state. The file will be automatically provided with the request.
     */
    saveAttachment(): void {
        console.log('form value', this.form.value);

        if (!this.attachmentToBeUpdatedOrCreated()) {
            return;
        }

        const updatedOrCreatedAttachment: Attachment = { ...this.attachmentToBeUpdatedOrCreated() };
        updatedOrCreatedAttachment.version!++;
        updatedOrCreatedAttachment.uploadDate = dayjs();
        updatedOrCreatedAttachment.name = this.form.value.attachmentName;
        updatedOrCreatedAttachment.releaseDate = this.form.value.releaseDate;

        if (!this.attachmentFile() && !updatedOrCreatedAttachment.id) {
            return;
        }

        if (updatedOrCreatedAttachment.id) {
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.attachmentService.update(updatedOrCreatedAttachment.id, updatedOrCreatedAttachment, this.attachmentFile(), requestOptions).subscribe({
                next: (attachmentRes: HttpResponse<Attachment>) => {
                    this.resetAttachmentFormVariables();
                    this.notificationText = undefined;
                    this.attachments = this.attachments.map((el) => {
                        return el.id === attachmentRes.body!.id ? attachmentRes.body! : el;
                    });
                },
                error: (error: HttpErrorResponse) => this.handleFailedUpload(error),
            });
        } else {
            this.attachmentService.create(updatedOrCreatedAttachment, this.attachmentFile()!).subscribe({
                next: (attachmentRes: HttpResponse<Attachment>) => {
                    this.attachments.push(attachmentRes.body!);
                    this.lectureService.findWithDetails(this.lecture().id!).subscribe((lectureResponse: HttpResponse<Lecture>) => {
                        this.lecture.set(lectureResponse.body!);
                    });
                    this.attachmentFile.set(undefined);
                    this.attachmentToBeUpdatedOrCreated.set(undefined);
                    this.attachmentBackup = undefined;
                    this.loadAttachments();
                    this.clearFormValues();
                },
                error: (error: HttpErrorResponse) => this.handleFailedUpload(error),
            });
        }
    }

    private clearFormValues(): void {
        this.form.reset({
            attachmentName: undefined,
            attachmentFileName: undefined,
            releaseDate: undefined,
            notificationText: undefined,
        });
    }

    private resetAttachmentFormVariables() {
        this.attachmentFile.set(undefined);
        this.attachmentToBeUpdatedOrCreated.set(undefined);
        this.attachmentBackup = undefined;
        this.clearFormValues();
    }

    private handleFailedUpload(error: HttpErrorResponse): void {
        this.errorMessage = error.message;
        this.erroredFile = this.attachmentFile();
        this.fileInput.nativeElement.value = '';
        this.attachmentFile.set(undefined);
        this.resetAttachment();
    }

    editAttachment(attachment: Attachment): void {
        if (this.fileInput) {
            this.fileInput.nativeElement.value = '';
        }

        // attachmentFileName can only be set to an empty string due to security reasons in current angular version (18)
        this.setFormValues({
            attachmentName: attachment?.name,
            releaseDate: dayjs(attachment?.releaseDate),
            notificationText: this.notificationText,
        });

        this.attachmentToBeUpdatedOrCreated.set(attachment);
        this.attachmentBackup = Object.assign({}, attachment, {});
    }

    private setFormValues(formValues: LectureAttachmentFormData): void {
        this.form.patchValue(formValues);
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
        this.attachmentToBeUpdatedOrCreated.set(undefined);
        this.erroredFile = undefined;
        this.clearFormValues();
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
            this.fileService.downloadFile(this.fileService.replaceLectureAttachmentPrefixAndUnderscores(downloadUrl));
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

        const file = input.files[0];
        this.attachmentFile.set(file);
        this.attachmentToBeUpdatedOrCreated()!.link = file.name;
        if (!this.attachmentToBeUpdatedOrCreated()!.name) {
            const derivedFileName = this.determineAttachmentNameBasedOnFileName(file.name);
            this.attachmentToBeUpdatedOrCreated()!.name = derivedFileName;
            this.form.patchValue({ attachmentName: derivedFileName });
        }
    }

    private determineAttachmentNameBasedOnFileName(fileName: string): string {
        const FILE_EXTENSION_REGEX = /\.[^/.]+$/;
        return fileName.replace(FILE_EXTENSION_REGEX, '');
    }
}
