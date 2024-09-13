import { Component, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-feedback-modal',
    templateUrl: './feedback-modal.component.html',
    styleUrls: ['./feedback-modal.component.scss'],
    imports: [ArtemisSharedCommonModule],
    standalone: true,
})
export class FeedbackModalComponent {
    @Input() feedbackDetail!: FeedbackDetail;

    constructor(public activeModal: NgbActiveModal) {}
}
