import { Component, Input, OnInit } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Participation } from 'app/entities/participation/participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';

type FeedbackDetail = {
    count: number;
    detailText: string;
};

@Component({
    selector: 'jhi-testcase-analysis',
    templateUrl: './testcase-analysis.component.html',
    styleUrls: ['./testcase-analysis.component.scss'],
})
export class TestcaseAnalysisComponent implements OnInit {
    @Input() exerciseTitle?: string;

    feedbacks: FeedbackDetail[] = [];

    constructor(
        private participationService: ParticipationService,
        private resultService: ResultService,
        private programmingExerciseTaskService: ProgrammingExerciseTaskService,
    ) {}

    ngOnInit(): void {
        if (this.programmingExerciseTaskService.exercise.id != undefined) {
            this.participationService.findAllParticipationsByExercise(this.programmingExerciseTaskService.exercise.id, true).subscribe((participationsResponse) => {
                this.loadFeedbacks(participationsResponse.body ?? []);
            });
        }
    }

    loadFeedbacks(participations: Participation[]): void {
        participations.forEach((participation) => {
            participation.results?.forEach((result) => {
                this.resultService.getFeedbackDetailsForResult(participation.id!, result).subscribe((response) => {
                    const feedbackArray = response.body ?? [];
                    this.saveFeedbacks(feedbackArray);
                });
            });
        });
    }

    saveFeedbacks(feedbackArray: Feedback[]): Feedback[] {
        feedbackArray.forEach((feedback) => {
            const feedbackText = feedback.text ?? '';
            const existingFeedback = this.feedbacks.find((f) => f.detailText === feedbackText);
            if (existingFeedback) {
                existingFeedback.count += 1;
                existingFeedback.detailText += `\n${feedback.detailText}`;
            } else {
                this.feedbacks.push({ count: 1, detailText: feedback.detailText ?? '' });
            }
        });
        this.sortFeedbacksByCount();
        return feedbackArray;
    }

    sortFeedbacksByCount(): void {
        this.feedbacks.sort((a, b) => b.count - a.count);
    }
}
