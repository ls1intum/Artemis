import { Component, ElementRef, ViewChild, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import dayjs from 'dayjs/esm';
import { AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import urlParser from 'js-video-url-parser';
import { faArrowLeft, faQuestionCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER, ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE } from 'app/shared/constants/file-extensions.constants';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { AccountService } from 'app/core/auth/account.service';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { TranscriptionStatus } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { AlertService } from 'app/shared/service/alert.service';
import { firstValueFrom } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';

export interface AttachmentVideoUnitFormData {
    formProperties: FormProperties;
    fileProperties: FileProperties;
    playlistUrl?: string;
    transcriptionProperties?: TranscriptionProperties;
    transcriptionStatus?: string;
}

// matches structure of the reactive form
export interface FormProperties {
    name?: string;
    description?: string;
    releaseDate?: dayjs.Dayjs;
    version?: number;
    updateNotificationText?: string;
    videoSource?: string;
    urlHelper?: string;
    competencyLinks?: CompetencyLectureUnitLink[];
    generateTranscript?: boolean;
    videoTranscription?: string;
}

// file input is a special case and is not included in the reactive form structure
export interface FileProperties {
    file?: File;
    fileName?: string;
}

export interface TranscriptionProperties {
    videoTranscription?: string;
}

function isTumLiveUrl(url: URL): boolean {
    const tumLiveUrls = ['live.rbg.tum.de', 'tum.live'];
    return tumLiveUrls.includes(url.host);
}

function isVideoOnlyTumUrl(url: URL): boolean {
    return url?.searchParams.get('video_only') === '1';
}

function videoSourceTransformUrlValidator(control: AbstractControl): ValidationErrors | undefined {
    const urlValue = control.value;
    if (!urlValue) {
        return undefined;
    }
    let parsedUrl, url;
    try {
        url = new URL(urlValue);
        parsedUrl = urlParser.parse(urlValue);
    } catch {
        //intentionally empty
    }
    // The URL is valid if it's a TUM-Live URL or if it can be parsed by the js-video-url-parser.
    if ((url && isTumLiveUrl(url)) || parsedUrl) {
        return undefined;
    }
    return { invalidVideoUrl: true };
}

function videoSourceUrlValidator(control: AbstractControl): ValidationErrors | undefined {
    const urlValue = control.value;
    if (!urlValue) {
        return undefined;
    }
    let url;
    try {
        url = new URL(control.value);
    } catch {
        // intentionally empty
    }
    if (url && !(isTumLiveUrl(url) && !isVideoOnlyTumUrl(url))) {
        return undefined;
    }
    return { invalidVideoUrl: true };
}

function validJsonOrEmpty(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    if (value === undefined || value === null || value === '') {
        return null;
    }
    try {
        JSON.parse(value);
        return null;
    } catch {
        return { invalidJson: true };
    }
}

@Component({
    selector: 'jhi-attachment-video-unit-form',
    templateUrl: './attachment-video-unit-form.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, FaIconComponent, NgbTooltip, FormDateTimePickerComponent, CompetencySelectionComponent, ArtemisTranslatePipe],
})
export class AttachmentVideoUnitFormComponent {
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faTimes = faTimes;
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly TranscriptionStatus = TranscriptionStatus;

    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;

    private readonly attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private readonly lectureTranscriptionService = inject(LectureTranscriptionService);
    private readonly alertService = inject(AlertService);
    private readonly modalService = inject(NgbModal);

    canGenerateTranscript = signal(false);
    playlistUrl = signal<string | undefined>(undefined);
    transcriptionStatus = signal<TranscriptionStatus | undefined>(undefined);
    isCancelling = signal(false);

    formData = input<AttachmentVideoUnitFormData>();
    isEditMode = input<boolean>(false);
    lectureUnitId = input<number | undefined>(undefined);

    formSubmitted = output<AttachmentVideoUnitFormData>();

    hasCancelButton = input<boolean>(false);
    onCancel = output<void>();

    datePickerComponent = viewChild(FormDateTimePickerComponent);

    // have to handle the file input as a special case at is not part of the reactive form
    @ViewChild('fileInput', { static: false })
    fileInput: ElementRef;
    file: File;
    fileInputTouched = false;

    fileName = signal<string | undefined>(undefined);
    isFileTooBig = signal<boolean>(false);

    videoSourceUrlValidator = videoSourceUrlValidator;
    videoSourceTransformUrlValidator = videoSourceTransformUrlValidator;

    private readonly formBuilder = inject(FormBuilder);
    private readonly accountService = inject(AccountService);

    readonly shouldShowTranscriptionCreation = computed(() => this.accountService.isAdmin());

    constructor() {
        effect(() => {
            const formData = this.formData();
            if (this.isEditMode() && formData) {
                this.setFormValues(formData);
                const newStatus = formData.transcriptionStatus ? (formData.transcriptionStatus as TranscriptionStatus) : undefined;
                this.transcriptionStatus.set(newStatus);

                // Set playlist URL if available from formData (for existing videos)
                if (formData.playlistUrl) {
                    this.playlistUrl.set(formData.playlistUrl);
                    this.canGenerateTranscript.set(true);
                }
            } else {
                this.transcriptionStatus.set(undefined);
            }
        });
    }

    form: FormGroup = this.formBuilder.group({
        name: [undefined as string | undefined, [Validators.required, Validators.maxLength(255)]],
        description: [undefined as string | undefined, [Validators.maxLength(1000)]],
        releaseDate: [undefined as dayjs.Dayjs | undefined],
        version: [{ value: 1, disabled: true }],
        videoSource: [undefined as string | undefined, this.videoSourceUrlValidator],
        urlHelper: [undefined as string | undefined, this.videoSourceTransformUrlValidator],
        updateNotificationText: [undefined as string | undefined, [Validators.maxLength(1000)]],
        competencyLinks: [undefined as CompetencyLectureUnitLink[] | undefined],
        videoTranscription: [undefined as string | undefined, [validJsonOrEmpty]],
        generateTranscript: [false],
    });
    private readonly statusChanges = toSignal(this.form.statusChanges ?? 'INVALID');

    readonly videoSourceSignal = toSignal(this.videoSourceControl!.valueChanges, { initialValue: this.videoSourceControl!.value });

    readonly shouldShowTranscriptCheckbox = computed(() => {
        const status = this.transcriptionStatus();
        const hasPlaylist = !!this.playlistUrl();

        // Don't show checkbox if no playlist URL
        if (!hasPlaylist) {
            return false;
        }

        // Don't show checkbox if transcription is pending/processing
        if (status === TranscriptionStatus.PENDING || status === TranscriptionStatus.PROCESSING) {
            return false;
        }

        // Show checkbox if:
        // 1. No transcription exists yet (status is undefined) OR
        // 2. Transcription is COMPLETED or FAILED (allow regeneration/overwrite)
        return true;
    });

    readonly showTranscriptionPendingWarning = computed(() => {
        const status = this.transcriptionStatus();
        return status === TranscriptionStatus.PENDING || status === TranscriptionStatus.PROCESSING;
    });

    readonly showTranscriptionOverwriteWarning = computed(() => {
        const status = this.transcriptionStatus();
        const hasPlaylist = !!this.playlistUrl();

        // Show overwrite warning when user can generate but a transcription already exists
        return hasPlaylist && (status === TranscriptionStatus.COMPLETED || status === TranscriptionStatus.FAILED);
    });

    readonly showTranscriptionStatusBadge = computed(() => {
        const status = this.transcriptionStatus();
        return this.isEditMode() && status && (status === TranscriptionStatus.PENDING || status === TranscriptionStatus.PROCESSING || status === TranscriptionStatus.FAILED);
    });

    isFormValid = computed(() => {
        return this.statusChanges() === 'VALID' && !this.isFileTooBig() && this.datePickerComponent()?.isValid() && (!!this.fileName() || !!this.videoSourceSignal());
    });

    onFileChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (!input.files?.length) {
            return;
        }
        this.file = input.files[0];
        this.fileName.set(this.file.name);
        // automatically set the name in case it is not yet specified
        if (this.form && (this.nameControl?.value == undefined || this.nameControl?.value == '')) {
            this.form.patchValue({
                // without extension
                name: this.file.name.replace(/\.[^/.]+$/, ''),
            });
        }
        this.isFileTooBig.set(this.file.size > MAX_FILE_SIZE);
    }

    get nameControl() {
        return this.form.get('name');
    }

    get descriptionControl() {
        return this.form.get('description');
    }

    get releaseDateControl() {
        return this.form.get('releaseDate');
    }

    get updateNotificationTextControl() {
        return this.form.get('updateNotificationText');
    }

    get versionControl() {
        return this.form.get('version');
    }

    get videoSourceControl() {
        return this.form.get('videoSource');
    }

    get urlHelperControl() {
        return this.form.get('urlHelper');
    }

    get videoTranscriptionControl() {
        return this.form.get('videoTranscription');
    }

    checkPlaylistAvailability(originalUrl: string): void {
        this.attachmentVideoUnitService.getPlaylistUrl(originalUrl).subscribe({
            next: (playlist) => {
                if (playlist) {
                    this.canGenerateTranscript.set(true);
                    this.playlistUrl.set(playlist);
                } else {
                    this.canGenerateTranscript.set(false);
                    this.playlistUrl.set(undefined);
                    this.form.get('generateTranscript')?.setValue(false);
                }
            },
            error: () => {
                this.canGenerateTranscript.set(false);
                this.playlistUrl.set(undefined);
                this.form.get('generateTranscript')?.setValue(false);
            },
        });
    }

    submitForm() {
        const formValue = this.form.value;
        const formProperties: FormProperties = { ...formValue };

        formProperties.videoTranscription = undefined;
        const fileProperties: FileProperties = {
            file: this.file,
            fileName: this.fileName(),
        };
        const transcriptionProperties: TranscriptionProperties = {
            videoTranscription: formValue.videoTranscription,
        };

        this.formSubmitted.emit({
            formProperties,
            fileProperties,
            transcriptionProperties,
            playlistUrl: this.playlistUrl(),
        });
    }

    private setFormValues(formData: AttachmentVideoUnitFormData) {
        if (formData?.formProperties) {
            this.form.patchValue(formData.formProperties);
        }
        if (formData?.fileProperties?.file) {
            this.file = formData?.fileProperties?.file;
        }
        if (formData?.fileProperties?.fileName) {
            this.fileName.set(formData?.fileProperties?.fileName);
        }
        if (formData?.transcriptionProperties) {
            this.form.patchValue(formData.transcriptionProperties);
        }
    }

    get isTransformable() {
        if (this.urlHelperControl!.value === undefined || this.urlHelperControl!.value === null || this.urlHelperControl!.value === '') {
            return false;
        } else {
            return !this.urlHelperControl?.invalid;
        }
    }

    setEmbeddedVideoUrl(event: any) {
        event.stopPropagation();

        const originalUrl = this.urlHelperControl!.value;
        const embeddedUrl = this.extractEmbeddedUrl(originalUrl);
        this.videoSourceControl!.setValue(embeddedUrl);

        this.checkPlaylistAvailability(originalUrl);
    }

    extractEmbeddedUrl(videoUrl: string) {
        const url = new URL(videoUrl);
        if (isTumLiveUrl(url)) {
            url.searchParams.set('video_only', '1');
            return url.toString();
        }
        const videoInfo = urlParser.parse(videoUrl);
        if (!videoInfo) {
            return videoUrl;
        }
        return urlParser.create({
            videoInfo,
            format: 'embed',
        });
    }

    cancelForm() {
        this.onCancel.emit();
    }

    async cancelTranscription() {
        const id = this.lectureUnitId();
        if (!id) {
            return;
        }

        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.title = 'artemisApp.attachmentVideoUnit.transcription.cancelButton';
        modalRef.componentInstance.text = 'artemisApp.attachmentVideoUnit.transcription.cancelConfirm';
        modalRef.componentInstance.translateText = true;

        try {
            await modalRef.result;
            // User confirmed
            this.isCancelling.set(true);
            try {
                const success = await firstValueFrom(this.lectureTranscriptionService.cancelTranscription(id));
                if (success) {
                    this.alertService.success('artemisApp.attachmentVideoUnit.transcription.cancelSuccess');
                    this.transcriptionStatus.set(undefined);
                } else {
                    this.alertService.error('artemisApp.attachmentVideoUnit.transcription.cancelError');
                }
            } catch (error) {
                this.alertService.error('artemisApp.attachmentVideoUnit.transcription.cancelError');
            } finally {
                this.isCancelling.set(false);
            }
        } catch {
            // User cancelled the modal - do nothing
        }
    }
}
