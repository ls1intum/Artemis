import { Component, inject, input, output, signal } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { FeedbackDetail } from 'app/programming/manage/grading/feedback-analysis/service/feedback-analysis.service';
import { ConfirmFeedbackChannelCreationModalComponent } from 'app/programming/manage/grading/feedback-analysis/modal/confirm-feedback-channel-creation/confirm-feedback-channel-creation-modal.component';
import { AlertService } from 'app/foundation/service/alert.service';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-feedback-detail-channel-modal',
    templateUrl: './feedback-detail-channel-modal.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, ArtemisTranslatePipe],
})
export class FeedbackDetailChannelModalComponent {
    protected readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis.feedbackDetailChannel';
    feedbackDetail = input.required<FeedbackDetail>();
    exerciseDueDate = input<dayjs.Dayjs | undefined>();
    currentDateTime = signal(dayjs());
    formSubmitted = output<{ channelDto: ChannelDTO; navigate: boolean }>();

    isConfirmModalOpen = signal(false);

    private alertService = inject(AlertService);
    private readonly formBuilder = inject(FormBuilder);
    private readonly activeModal = inject(NgbActiveModal);
    private readonly modalService = inject(NgbModal);
    form: FormGroup = this.formBuilder.group({
        name: ['', [Validators.required, Validators.maxLength(30), Validators.pattern('^[a-z0-9-]{1}[a-z0-9-]{0,30}$')]],
        description: ['', [Validators.required, Validators.maxLength(250)]],
        isPrivate: [true, Validators.required],
        isAnnouncementChannel: [false, Validators.required],
    });

    async submitForm(navigate: boolean): Promise<void> {
        if (this.form.valid && !this.isConfirmModalOpen()) {
            this.isConfirmModalOpen.set(true);
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
            this.isConfirmModalOpen.set(false);
        }
    }

    async handleModal(): Promise<boolean> {
        try {
            const modalRef = this.modalService.open(ConfirmFeedbackChannelCreationModalComponent, { centered: true });
            // affectedStudentsCount is a signal input(); when the modal is opened imperatively via NgbModal it must
            // be assigned a signal (not a plain number) so that calling affectedStudentsCount() in the template works.
            modalRef.componentInstance.affectedStudentsCount = signal(this.feedbackDetail().count);
            return await modalRef.result;
        } catch (error) {
            this.alertService.error(error);
            return false;
        }
    }

    closeModal(): void {
        this.activeModal.close();
    }

    dismissModal(): void {
        this.activeModal.dismiss();
    }
}
