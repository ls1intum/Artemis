import { Component, Input, OnInit, inject } from '@angular/core';
import { FeedbackAnalysisService, FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-feedback-analysis',
    templateUrl: './feedback-analysis.component.html',
    standalone: true,
    imports: [ArtemisSharedModule],
    providers: [FeedbackAnalysisService],
})
export class FeedbackAnalysisComponent implements OnInit {
    private feedbackAnalysisService = inject(FeedbackAnalysisService);
    private alertService = inject(AlertService);

    @Input() exerciseTitle: string;
    @Input() exerciseId: number;
    feedbackDetails: FeedbackDetail[] = [];

    ngOnInit(): void {
        this.loadFeedbackDetails(this.exerciseId);
    }

    async loadFeedbackDetails(exerciseId: number): Promise<void> {
        try {
            this.feedbackDetails = await this.feedbackAnalysisService.getFeedbackDetailsForExercise(exerciseId);
        } catch (error) {
            this.alertService.error(`artemisApp.programmingExercise.configureGrading.feedbackAnalysis.error`);
        }
    }
}
