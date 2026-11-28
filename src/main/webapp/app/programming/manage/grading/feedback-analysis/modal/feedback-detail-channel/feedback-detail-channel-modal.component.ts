import { Component, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiDialogComponent } from '@tumaet/ui-angular/dialog';
import { TumAetUiInputDirective } from '@tumaet/ui-angular/input';
import { TumAetUiMessageComponent } from '@tumaet/ui-angular/message';
import { TumAetUiSelectButtonComponent } from '@tumaet/ui-angular/select-button';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { FeedbackDetail } from 'app/programming/manage/grading/feedback-analysis/service/feedback-analysis.service';
import { ConfirmFeedbackChannelCreationModalComponent } from 'app/programming/manage/grading/feedback-analysis/modal/confirm-feedback-channel-creation/confirm-feedback-channel-creation-modal.component';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-feedback-detail-channel-modal',
    templateUrl: './feedback-detail-channel-modal.component.html',
    imports: [
        FormsModule,
        ReactiveFormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumAetUiDialogComponent,
        TumAetUiButtonDirective,
        TumAetUiInputDirective,
        TumAetUiMessageComponent,
        TumAetUiSelectButtonComponent,
        ConfirmFeedbackChannelCreationModalComponent,
    ],
})
export class FeedbackDetailChannelModalComponent {
    private readonly formBuilder = inject(FormBuilder);
    private readonly translateService = inject(TranslateService);

    protected readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis.feedbackDetailChannel';
    feedbackDetail = input.required<FeedbackDetail>();
    exerciseDueDate = input<dayjs.Dayjs | undefined>();
    currentDateTime = signal(dayjs());
    formSubmitted = output<{ channelDto: ChannelDTO; navigate: boolean }>();
    /** Emits once the dialog was closed, whether a channel was requested or not, so the host can remove it. */
    closed = output<void>();

    isConfirmModalOpen = signal(false);
    /** Settles the pending {@link handleModal} call once the confirmation dialog reports its result. */
    private resolveConfirmation?: (confirmed: boolean) => void;

    private readonly currentLanguage = toSignal(this.translateService.onLangChange);
    protected readonly visibilityOptions = computed(() => {
        this.currentLanguage();
        return [
            { label: this.translateService.instant(this.TRANSLATION_BASE + '.visibilityPublic'), value: false },
            { label: this.translateService.instant(this.TRANSLATION_BASE + '.visibilityPrivate'), value: true },
        ];
    });
    protected readonly announcementOptions = computed(() => {
        this.currentLanguage();
        return [
            { label: this.translateService.instant(this.TRANSLATION_BASE + '.announcementChannelYes'), value: true },
            { label: this.translateService.instant(this.TRANSLATION_BASE + '.announcementChannelNo'), value: false },
        ];
    });

    form: FormGroup = this.formBuilder.group({
        name: ['', [Validators.required, Validators.maxLength(30), Validators.pattern('^[a-z0-9-]{1}[a-z0-9-]{0,30}$')]],
        description: ['', [Validators.required, Validators.maxLength(250)]],
        isPrivate: [true, Validators.required],
        isAnnouncementChannel: [false, Validators.required],
    });

    async submitForm(navigate: boolean): Promise<void> {
        if (this.form.valid && !this.isConfirmModalOpen()) {
            const result = await this.handleModal();
            if (result) {
                const channelDTO = new ChannelDTO();
                channelDTO.name = this.form.get('name')?.value;
                channelDTO.description = this.form.get('description')?.value;
                channelDTO.topic = 'FeedbackDiscussion';
                channelDTO.isPublic = !this.form.get('isPrivate')?.value || false;
                channelDTO.isAnnouncementChannel = this.form.get('isAnnouncementChannel')?.value || false;

                this.formSubmitted.emit({ channelDto: channelDTO, navigate });
                this.closeModal();
            }
        }
    }

    /**
     * Shows the confirmation dialog and waits for the answer.
     *
     * @returns whether the creation of the channel was confirmed
     */
    handleModal(): Promise<boolean> {
        return new Promise<boolean>((resolve) => {
            this.resolveConfirmation = resolve;
            this.isConfirmModalOpen.set(true);
        });
    }

    protected onConfirmationClosed(confirmed: boolean): void {
        this.isConfirmModalOpen.set(false);
        this.resolveConfirmation?.(confirmed);
        this.resolveConfirmation = undefined;
    }

    closeModal(): void {
        this.closed.emit();
    }

    dismissModal(): void {
        this.closed.emit();
    }

    protected onVisibleChange(visible: boolean): void {
        if (!visible) {
            this.dismissModal();
        }
    }
}
