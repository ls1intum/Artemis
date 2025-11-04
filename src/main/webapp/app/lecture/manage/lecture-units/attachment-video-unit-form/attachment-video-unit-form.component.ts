import { Component, ElementRef, OnChanges, ViewChild, computed, inject, input, output, signal, viewChild } from '@angular/core';
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
import { HttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';

export interface AttachmentVideoUnitFormData {
    formProperties: FormProperties;
    fileProperties: FileProperties;
    playlistUrl?: string;
    transcriptionProperties?: TranscriptionProperties;
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
export class AttachmentVideoUnitFormComponent implements OnChanges {
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faTimes = faTimes;
    protected readonly faArrowLeft = faArrowLeft;

    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;

    private readonly http = inject(HttpClient);
    canGenerateTranscript = signal(false);
    playlistUrl = signal<string | undefined>(undefined);

    formData = input<AttachmentVideoUnitFormData>();
    isEditMode = input<boolean>(false);

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

    readonly shouldShowTranscriptCheckbox = computed(() => !!this.playlistUrl());

    isFormValid = computed(() => {
        return this.statusChanges() === 'VALID' && !this.isFileTooBig() && this.datePickerComponent()?.isValid() && (!!this.fileName() || !!this.videoSourceSignal());
    });

    ngOnChanges() {
        if (this.isEditMode() && this.formData()) {
            this.setFormValues(this.formData()!);
        }
    }

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

    checkTumLivePlaylist(originalUrl: string): void {
        const parsedUrl = new URL(originalUrl);

        if (parsedUrl.host === 'live.rbg.tum.de') {
            this.http
                .get('api/nebula/video-utils/tum-live-playlist', {
                    params: { url: originalUrl },
                    responseType: 'text',
                })
                .subscribe({
                    next: (playlist) => {
                        this.canGenerateTranscript.set(true);
                        this.playlistUrl.set(playlist);
                    },
                    error: (error) => {
                        this.canGenerateTranscript.set(false);
                        this.playlistUrl.set(undefined);
                        this.form.get('generateTranscript')?.setValue(false);
                    },
                });
        } else {
            this.canGenerateTranscript.set(false);
            this.playlistUrl.set(undefined);
            this.form.get('generateTranscript')?.setValue(false);
        }
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

        this.checkTumLivePlaylist(originalUrl);
    }

    extractEmbeddedUrl(videoUrl: string) {
        const url = new URL(videoUrl);
        if (isTumLiveUrl(url)) {
            url.searchParams.set('video_only', '1');
            return url.toString();
        }
        return urlParser.create({
            videoInfo: urlParser.parse(videoUrl)!,
            format: 'embed',
        });
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
