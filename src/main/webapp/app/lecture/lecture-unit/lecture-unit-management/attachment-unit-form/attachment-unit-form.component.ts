import { Component, ElementRef, EventEmitter, Input, OnChanges, OnInit, Output, ViewChild } from '@angular/core';
import { Moment } from 'moment';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';

export class AttachmentUnitFormData {
    name?: string;
    description?: string;
    releaseDate?: Moment;
    file?: Blob | File;
    fileName?: string;
}

@Component({
    selector: 'jhi-attachment-unit-form',
    templateUrl: './attachment-unit-form.component.html',
    styleUrls: ['./attachment-unit-form.component.scss'],
})
export class AttachmentUnitFormComponent implements OnInit, OnChanges {
    @Input()
    attachmentUnit: AttachmentUnit;

    @Input()
    editing = false;

    erroredFile?: Blob;
    errorMessage?: string;

    @Output()
    submitAttachmentUnitForm: EventEmitter<AttachmentUnitFormData> = new EventEmitter<AttachmentUnitFormData>();

    form: FormGroup;

    @ViewChild('fileInput', { static: false })
    fileInput: ElementRef;

    file?: Blob | File;
    // Setting it to the placeholder value as default
    fileName?: string = this.translateService.instant('artemisApp.attachmentUnit.createAttachmentUnit.chooseFile');

    constructor(private translateService: TranslateService, private fb: FormBuilder) {}

    ngOnChanges(): void {
        this.initForm();
    }

    ngOnInit(): void {
        this.initForm();
    }

    private initForm() {
        if (this.form) {
            return;
        }
        this.form = this.fb.group({
            name: [undefined, [Validators.required, Validators.maxLength(255)]],
            description: [undefined, [Validators.maxLength(255)]],
            file: [undefined, Validators.required],
            releaseDate: [undefined],
        });
    }

    setFile($event: any): void {
        if ($event.target.files.length) {
            this.erroredFile = undefined; // removes the file size error message when the user selects a new file
            const fileList: FileList = $event.target.files;
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

    get fileControl() {
        return this.form.get('file');
    }

    resetForm() {
        this.form.reset();
        this.fileName = this.translateService.instant('artemisApp.attachmentUnit.createAttachmentUnit.chooseFile');
        this.erroredFile = undefined;
        this.errorMessage = undefined;
    }

    setFileUploadError(error: any) {
        this.errorMessage = error.message;
        this.erroredFile = this.file;
        this.fileInput.nativeElement.value = '';
        this.file = undefined;
    }

    submitForm() {
        const formValue = this.form.value;
        const formData: AttachmentUnitFormData = new AttachmentUnitFormData();
        if (formValue.name) {
            formData.name = formValue.name;
        }
        if (formValue.description) {
            formData.description = formValue.description;
        }
        if (formValue.releaseDate) {
            formData.releaseDate = formValue.releaseDate;
        }
        if (this.file) {
            formData.file = this.file;
        }
        if (this.fileName) {
            formData.fileName = this.fileName;
        }

        this.submitAttachmentUnitForm.emit(formData);
    }
}
