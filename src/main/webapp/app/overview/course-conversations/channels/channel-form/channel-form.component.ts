import { Component, EventEmitter, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

export interface ChannelFormData {
    name?: string;
    description?: string;
    isPublic?: boolean;
}

export type ChannelType = 'PUBLIC' | 'PRIVATE';

const channelRegex: RegExp = new RegExp('^[a-z0-9-]{1}[a-z0-9-]{0,20}$');

@Component({
    selector: 'jhi-channel-form',
    templateUrl: './channel-form.component.html',
    styleUrls: ['./channel-form.component.scss'],
})
export class ChannelFormComponent implements OnInit, OnChanges {
    formData: ChannelFormData = {
        name: undefined,
        description: undefined,
        isPublic: undefined,
    };

    @Output() formSubmitted: EventEmitter<ChannelFormData> = new EventEmitter<ChannelFormData>();
    @Output() channelTypeChanged: EventEmitter<ChannelType> = new EventEmitter<ChannelType>();

    form: FormGroup;

    constructor(private fb: FormBuilder) {}

    get nameControl() {
        return this.form.get('name');
    }

    get descriptionControl() {
        return this.form.get('description');
    }

    get isPublicControl() {
        return this.form.get('isPublic');
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    ngOnInit(): void {
        this.initializeForm();
    }
    ngOnChanges(): void {
        this.initializeForm();
    }

    submitForm() {
        this.formSubmitted.emit({ ...this.form.value } as ChannelFormData);
    }

    private initializeForm() {
        if (this.form) {
            return;
        }

        this.form = this.fb.group({
            name: [undefined, [Validators.required, Validators.maxLength(20), Validators.pattern(channelRegex)]],
            description: [undefined, [Validators.maxLength(250)]],
            isPublic: [true, [Validators.required]],
        });

        if (this.isPublicControl) {
            this.isPublicControl.valueChanges.subscribe((value) => {
                this.channelTypeChanged.emit(value ? 'PUBLIC' : 'PRIVATE');
            });
        }
    }
}
