import { Component, OnDestroy, OnInit, inject, output } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { Subject, takeUntil } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SelectButton } from 'primeng/selectbutton';
import { TranslateService } from '@ngx-translate/core';

export interface ChannelFormData {
    name?: string;
    description?: string;
    isPublic?: boolean;
    isAnnouncementChannel?: boolean;
    isCourseWideChannel?: boolean;
}

export type ChannelType = 'PUBLIC' | 'PRIVATE';

export const channelRegex = new RegExp('^[a-z0-9-]{1}[a-z0-9:\\-]{0,30}$');

@Component({
    selector: 'jhi-channel-form',
    templateUrl: './channel-form.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, ChannelIconComponent, ArtemisTranslatePipe, SelectButton],
})
export class ChannelFormComponent implements OnInit, OnDestroy {
    private fb = inject(FormBuilder);
    private translateService = inject(TranslateService);

    private ngUnsubscribe = new Subject<void>();

    visibilityOptions: { label: string; value: boolean }[] = [];
    scopeOptions: { label: string; value: boolean }[] = [];
    typeOptions: { label: string; value: boolean }[] = [];

    formData: ChannelFormData = {
        name: undefined,
        description: undefined,
        isPublic: undefined,
        isAnnouncementChannel: undefined,
        isCourseWideChannel: undefined,
    };
    readonly formSubmitted = output<ChannelFormData>();
    readonly channelTypeChanged = output<ChannelType>();
    readonly isAnnouncementChannelChanged = output<boolean>();
    isCourseWideChannelChanged = output<boolean>();

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

    get isCourseWideChannelControl() {
        return this.form.get('isCourseWideChannel');
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    ngOnInit(): void {
        this.visibilityOptions = [
            { label: this.translateService.instant('artemisApp.dialogs.createChannel.channelForm.isPublicInput.public'), value: true },
            { label: this.translateService.instant('artemisApp.dialogs.createChannel.channelForm.isPublicInput.private'), value: false },
        ];
        this.scopeOptions = [
            { label: this.translateService.instant('artemisApp.dialogs.createChannel.channelForm.isCourseWideChannelInput.true'), value: true },
            { label: this.translateService.instant('artemisApp.dialogs.createChannel.channelForm.isCourseWideChannelInput.false'), value: false },
        ];
        this.typeOptions = [
            { label: this.translateService.instant('artemisApp.dialogs.createChannel.channelForm.isAnnouncementChannelInput.true'), value: true },
            { label: this.translateService.instant('artemisApp.dialogs.createChannel.channelForm.isAnnouncementChannelInput.false'), value: false },
        ];
        this.initializeForm();
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
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
            isCourseWideChannel: [false, [Validators.required]],
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

        if (this.isCourseWideChannelControl) {
            this.isCourseWideChannelControl.valueChanges.pipe(takeUntil(this.ngUnsubscribe)).subscribe((value) => {
                this.isCourseWideChannelChanged.emit(value);
            });
        }
    }
}
