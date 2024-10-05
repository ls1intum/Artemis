import { Component, EventEmitter, OnChanges, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';

export interface ChannelFormData {
    name?: string;
    description?: string;
    isPublic?: boolean;
    isAnnouncementChannel?: boolean;
}

export type ChannelType = 'PUBLIC' | 'PRIVATE';

export const channelRegex = new RegExp('^[a-z0-9-]{1}[a-z0-9-]{0,30}$');

@Component({
    selector: 'jhi-channel-form',
    templateUrl: './channel-form.component.html',
})
export class ChannelFormComponent implements OnInit, OnChanges, OnDestroy {
    private fb = inject(FormBuilder);

    private ngUnsubscribe = new Subject<void>();

    formData: ChannelFormData = {
        name: undefined,
        description: undefined,
        isPublic: undefined,
        isAnnouncementChannel: undefined,
    };
    @Output() formSubmitted: EventEmitter<ChannelFormData> = new EventEmitter<ChannelFormData>();
    @Output() channelTypeChanged: EventEmitter<ChannelType> = new EventEmitter<ChannelType>();
    @Output() isAnnouncementChannelChanged: EventEmitter<boolean> = new EventEmitter<boolean>();

    form: FormGroup;

    get nameControl() {
        return this.form.get('name');
    }

    get descriptionControl() {
        return this.form.get('description');
    }

    get isPublicControl() {
        return this.form.get('isPublic');
    }

    get isisAnnouncementChannelControl() {
        return this.form.get('isAnnouncementChannel');
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    ngOnInit(): void {
        this.initializeForm();
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
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
            isAnnouncementChannel: [false, [Validators.required]],
        });

        if (this.isPublicControl) {
            this.isPublicControl.valueChanges.pipe(takeUntil(this.ngUnsubscribe)).subscribe((value) => {
                this.channelTypeChanged.emit(value ? 'PUBLIC' : 'PRIVATE');
            });
        }

        if (this.isisAnnouncementChannelControl) {
            this.isisAnnouncementChannelControl.valueChanges.pipe(takeUntil(this.ngUnsubscribe)).subscribe((value) => {
                this.isAnnouncementChannelChanged.emit(value);
            });
        }
    }
}
