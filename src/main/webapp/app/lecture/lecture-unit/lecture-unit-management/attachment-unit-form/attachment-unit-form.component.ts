import { Component, ElementRef, EventEmitter, Input, OnChanges, OnInit, Output, ViewChild } from '@angular/core';
import dayjs from 'dayjs/esm';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';

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
}

// file input is a special case and is not included in the reactive form structure
export interface FileProperties {
    file?: File | Blob;
    fileName?: string;
}

@Component({
    selector: 'jhi-attachment-unit-form',
    templateUrl: './attachment-unit-form.component.html',
})
export class AttachmentUnitFormComponent implements OnInit, OnChanges {
    @Input()
    formData: AttachmentUnitFormData;
    @Input()
    isEditMode = false;

    fileUploadErrorMessage?: string;

    @Output()
    formSubmitted: EventEmitter<AttachmentUnitFormData> = new EventEmitter<AttachmentUnitFormData>();
    form: FormGroup;

    // have to handle the file input as a special case at is not part of the reactive form
    @ViewChild('fileInput', { static: false })
    fileInput: ElementRef;
    file: File | Blob;
    readonly fileNamePlaceholder = this.translateService.instant('artemisApp.attachmentUnit.createAttachmentUnit.chooseFile');
    fileName: string = this.fileNamePlaceholder;
    fileInputTouched = false;

    constructor(private translateService: TranslateService, private fb: FormBuilder) {}

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
            version: [1],
            updateNotificationText: [undefined as string | undefined, [Validators.maxLength(1000)]],
        });
    }

    onFileChange(event: any): void {
        if (event.target.files.length) {
            this.fileUploadErrorMessage = undefined; // removes the file size error message when the user selects a new file
            const fileList = event.target.files;
            this.file = fileList[0];
            this.fileName = this.file['name'];
        }
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
        return !(this.form.invalid || this.fileUploadErrorMessage || this.fileName === this.fileNamePlaceholder);
    }

    // will be called from parent component to set the form error when the file upload failed
    setFileUploadError(errorMessage: string) {
        this.fileUploadErrorMessage = errorMessage;
        this.fileInput.nativeElement.value = '';
        this.fileName = this.fileNamePlaceholder;
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
}
