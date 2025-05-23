import { Component, ElementRef, OnChanges, ViewChild, computed, inject, input, output, signal, viewChild } from '@angular/core';
import dayjs from 'dayjs/esm';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { faQuestionCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
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

export interface AttachmentUnitFormData {
    formProperties: FormProperties;
    fileProperties: FileProperties;
}

// matches structure of the reactive form
export interface FormProperties {
    name?: string;
    description?: string;
    releaseDate?: dayjs.Dayjs;
    version?: number;
    updateNotificationText?: string;
    competencyLinks?: CompetencyLectureUnitLink[];
}

// file input is a special case and is not included in the reactive form structure
export interface FileProperties {
    file?: File;
    fileName?: string;
}

@Component({
    selector: 'jhi-attachment-unit-form',
    templateUrl: './attachment-unit-form.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, FaIconComponent, NgbTooltip, FormDateTimePickerComponent, CompetencySelectionComponent, ArtemisTranslatePipe],
})
export class AttachmentUnitFormComponent implements OnChanges {
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faTimes = faTimes;

    protected readonly allowedFileExtensions = ALLOWED_FILE_EXTENSIONS_HUMAN_READABLE;
    protected readonly acceptedFileExtensionsFileBrowser = ACCEPTED_FILE_EXTENSIONS_FILE_BROWSER;

    formData = input<AttachmentUnitFormData>();
    isEditMode = input<boolean>(false);

    formSubmitted = output<AttachmentUnitFormData>();

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

    private readonly formBuilder = inject(FormBuilder);
    form: FormGroup = this.formBuilder.group({
        name: [undefined as string | undefined, [Validators.required, Validators.maxLength(255)]],
        description: [undefined as string | undefined, [Validators.maxLength(1000)]],
        releaseDate: [undefined as dayjs.Dayjs | undefined],
        version: [{ value: 1, disabled: true }],
        updateNotificationText: [undefined as string | undefined, [Validators.maxLength(1000)]],
        competencyLinks: [undefined as CompetencyLectureUnitLink[] | undefined],
    });
    private readonly statusChanges = toSignal(this.form.statusChanges ?? 'INVALID');

    isFormValid = computed(() => {
        return (this.statusChanges() === 'VALID' || this.fileName()) && !this.isFileTooBig() && this.datePickerComponent()?.isValid();
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

    submitForm() {
        const formValue = this.form.value;
        const formProperties: FormProperties = { ...formValue };
        const fileProperties: FileProperties = {
            file: this.file,
            fileName: this.fileName(),
        };

        this.formSubmitted.emit({
            formProperties,
            fileProperties,
        });
    }

    private setFormValues(formData: AttachmentUnitFormData) {
        if (formData?.formProperties) {
            this.form.patchValue(formData.formProperties);
        }
        if (formData?.fileProperties?.file) {
            this.file = formData?.fileProperties?.file;
        }
        if (formData?.fileProperties?.fileName) {
            this.fileName.set(formData?.fileProperties?.fileName);
        }
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
