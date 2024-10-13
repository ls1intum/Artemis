import { Component, ElementRef, EventEmitter, Input, OnChanges, OnInit, Output, ViewChild, inject } from '@angular/core';
import dayjs from 'dayjs/esm';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { faQuestionCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { UPLOAD_FILE_EXTENSIONS } from 'app/shared/constants/file-extensions.constants';
import { Competency } from 'app/entities/competency.model';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';

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
    competencies?: Competency[];
}

// file input is a special case and is not included in the reactive form structure
export interface FileProperties {
    file?: File;
    fileName?: string;
}

@Component({
    selector: 'jhi-attachment-unit-form',
    templateUrl: './attachment-unit-form.component.html',
})
export class AttachmentUnitFormComponent implements OnInit, OnChanges {
    private translateService = inject(TranslateService);
    private fb = inject(FormBuilder);

    @Input()
    formData: AttachmentUnitFormData;
    @Input()
    isEditMode = false;

    // A human-readable list of allowed file extensions
    readonly allowedFileExtensions = UPLOAD_FILE_EXTENSIONS.join(', ');
    // The list of file extensions for the "accept" attribute of the file input field
    readonly acceptedFileExtensionsFileBrowser = UPLOAD_FILE_EXTENSIONS.map((ext) => '.' + ext).join(',');

    faQuestionCircle = faQuestionCircle;

    @Output()
    formSubmitted: EventEmitter<AttachmentUnitFormData> = new EventEmitter<AttachmentUnitFormData>();
    form: FormGroup;

    @Input()
    hasCancelButton: boolean;
    @Output()
    onCancel: EventEmitter<any> = new EventEmitter<any>();

    faTimes = faTimes;

    // have to handle the file input as a special case at is not part of the reactive form
    @ViewChild('fileInput', { static: false })
    fileInput: ElementRef;
    file: File;
    fileName?: string;
    fileInputTouched = false;
    isFileTooBig: boolean;

    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    ngOnInit(): void {
        this.initializeForm();
    }

    private initializeForm() {
        if (this.form) {
            return;
        }
        this.form = this.fb.group({
            name: [undefined as string | undefined, [Validators.required, Validators.maxLength(255)]],
            description: [undefined as string | undefined, [Validators.maxLength(1000)]],
            releaseDate: [undefined as dayjs.Dayjs | undefined],
            version: [{ value: 1, disabled: true }],
            updateNotificationText: [undefined as string | undefined, [Validators.maxLength(1000)]],
            competencies: [undefined as Competency[] | undefined],
        });
    }

    onFileChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (!input.files?.length) {
            return;
        }
        this.file = input.files[0];
        this.fileName = this.file.name;
        // automatically set the name in case it is not yet specified
        if (this.form && (this.nameControl?.value == undefined || this.nameControl?.value == '')) {
            this.form.patchValue({
                // without extension
                name: this.file.name.replace(/\.[^/.]+$/, ''),
            });
        }
        this.isFileTooBig = this.file.size > MAX_FILE_SIZE;
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

    get isSubmitPossible() {
        return !(this.form.invalid || !this.fileName) && !this.isFileTooBig;
    }

    submitForm() {
        const formValue = this.form.value;
        const formProperties: FormProperties = { ...formValue };
        const fileProperties: FileProperties = {
            file: this.file,
            fileName: this.fileName,
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
            this.fileName = formData?.fileProperties?.fileName;
        }
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
