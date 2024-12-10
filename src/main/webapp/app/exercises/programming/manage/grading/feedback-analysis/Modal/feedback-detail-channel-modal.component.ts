import { Component, inject, input, output, signal } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { ConfirmFeedbackChannelCreationModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/confirm-feedback-channel-creation-modal.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-feedback-detail-channel-modal',
    templateUrl: './feedback-detail-channel-modal.component.html',
    imports: [ArtemisSharedCommonModule],
    standalone: true,
})
export class FeedbackDetailChannelModalComponent {
    protected readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis.feedbackDetailChannel';
    feedbackDetail = input.required<FeedbackDetail>();
    groupFeedback = input.required<boolean>();
    formSubmitted = output<{ channelDto: ChannelDTO; navigate: boolean }>();

    isConfirmModalOpen = signal(false);

    private alertService = inject(AlertService);
    private readonly formBuilder = inject(FormBuilder);
    private readonly activeModal = inject(NgbActiveModal);
    private readonly modalService = inject(NgbModal);
    form: FormGroup = this.formBuilder.group({
        name: ['', [Validators.required, Validators.maxLength(30), Validators.pattern('^[a-z0-9-]{1}[a-z0-9-]{0,30}$')]],
        description: ['', [Validators.required, Validators.maxLength(250)]],
        isPublic: [true, Validators.required],
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
                channelDTO.isPublic = this.form.get('isPublic')?.value;
                channelDTO.isAnnouncementChannel = this.form.get('isAnnouncementChannel')?.value;

                this.formSubmitted.emit({ channelDto: channelDTO, navigate });
                this.closeModal();
            }
            this.isConfirmModalOpen.set(false);
        }
    }

    async handleModal(): Promise<boolean> {
        try {
            const modalRef = this.modalService.open(ConfirmFeedbackChannelCreationModalComponent, { centered: true });
            modalRef.componentInstance.affectedStudentsCount = this.feedbackDetail().count;
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
