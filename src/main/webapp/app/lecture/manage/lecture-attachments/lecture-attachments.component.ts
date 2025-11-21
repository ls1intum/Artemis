import { Component, ElementRef, OnDestroy, ViewChild, computed, effect, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import dayjs from 'dayjs/esm';
import { Subject, Subscription } from 'rxjs';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { AttachmentService } from 'app/lecture/manage/services/attachment.service';
import { faPaperclip, faPencilAlt, faQuestionCircle, faSpinner, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER, ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE } from 'app/shared/constants/file-extensions.constants';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { FileService } from 'app/shared/service/file.service';

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
    imports: [
        TranslateDirective,
        NgClass,
        FaIconComponent,
        NgbTooltip,
        DeleteButtonDirective,
        FormsModule,
        RouterLink,
        ReactiveFormsModule,
        FormDateTimePickerComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        HtmlForMarkdownPipe,
    ],
})
export class LectureAttachmentsComponent implements OnDestroy {
    protected readonly faSpinner = faSpinner;
    protected readonly faTimes = faTimes;
    protected readonly faTrash = faTrash;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faPaperclip = faPaperclip;
    protected readonly faQuestionCircle = faQuestionCircle;

    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;

    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly attachmentService = inject(AttachmentService);
    private readonly lectureService = inject(LectureService);
    private readonly fileService = inject(FileService);
    private readonly formBuilder = inject(FormBuilder);

    @ViewChild('fileInput', { static: false }) fileInput: ElementRef;
    datePickerComponent = viewChild(FormDateTimePickerComponent);

    lectureId: number;

    lecture = signal<Lecture>(new Lecture());
    attachments: Attachment[] = [];
    attachmentToBeUpdatedOrCreated = signal<Attachment | undefined>(undefined);
    attachmentBackup?: Attachment;
    attachmentFile = signal<File | undefined>(undefined);
    isDownloadingAttachmentLink?: string;
    notificationText?: string;
    erroredFile?: File;
    errorMessage?: string;

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
    isFormValid = computed(
        () => !this.attachmentToBeUpdatedOrCreated() || (this.statusChanges() === 'VALID' && this.isFileSelectionValid() && this.datePickerComponent()?.isValid()),
    );

    constructor() {
        effect(() => {
            this.notificationText = undefined;
            this.routeDataSubscription?.unsubscribe(); // in case the subscription was already defined
            this.routeDataSubscription = this.activatedRoute.data.subscribe(({ lecture }) => {
                if (this.lectureId) {
                    this.lectureService.findWithDetails(this.lectureId).subscribe((lectureResponse: HttpResponse<Lecture>) => {
                        this.lecture.set(lectureResponse.body!);
                        this.loadAttachments();
                    });
                } else {
                    this.lecture.set(lecture);
                    this.loadAttachments();
                }
            });
        });
    }

    loadAttachments(): void {
        this.attachmentService.findAllByLectureId(this.lecture().id!).subscribe((attachmentsResponse: HttpResponse<Attachment[]>) => {
            this.attachments = attachmentsResponse.body!;
        });
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
        this.routeDataSubscription?.unsubscribe();
    }

    /**
     * If there is an attachment to save, it will be created or updated depending on its current state. The file will be automatically provided with the request.
     */
    saveAttachment(): void {
        if (!this.attachmentToBeUpdatedOrCreated()) {
            return;
        }
        this.attachmentToBeUpdatedOrCreated()!.version!++;
        this.attachmentToBeUpdatedOrCreated()!.uploadDate = dayjs();
        this.attachmentToBeUpdatedOrCreated()!.name = this.form.value.attachmentName ?? undefined;
        this.attachmentToBeUpdatedOrCreated()!.releaseDate = this.form.value.releaseDate ?? undefined;
        this.notificationText = this.form.value.notificationText ?? undefined;

        if (!this.attachmentFile() && !this.attachmentToBeUpdatedOrCreated()!.id) {
            return;
        }

        if (this.attachmentToBeUpdatedOrCreated()!.id) {
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.attachmentService.update(this.attachmentToBeUpdatedOrCreated()!.id!, this.attachmentToBeUpdatedOrCreated()!, this.attachmentFile(), requestOptions).subscribe({
                next: (attachmentRes: HttpResponse<Attachment>) => {
                    this.resetAttachmentFormVariables();
                    this.notificationText = undefined;
                    this.attachments = this.attachments.map((el) => {
                        return el.id === attachmentRes.body!.id ? attachmentRes.body! : el;
                    });
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

    private setFormValues(formValues: LectureAttachmentFormData): void {
        this.form.patchValue(formValues);
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
        this.resetAttachmentFormVariables();
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

    trackId(_index: number, item: Attachment): number | undefined {
        return item.id;
    }

    downloadAttachment(downloadName: string, downloadUrl: string): void {
        if (!this.isDownloadingAttachmentLink) {
            this.isDownloadingAttachmentLink = downloadUrl;
            this.fileService.downloadFileByAttachmentName(downloadUrl, downloadName);
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

        this.attachmentToBeUpdatedOrCreated()!.name = this.form.value.attachmentName ?? undefined;
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
