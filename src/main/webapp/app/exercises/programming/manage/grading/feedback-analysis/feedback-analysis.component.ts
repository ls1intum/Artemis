import { Component, Input, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { concatMap, tap } from 'rxjs/operators';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FeedbackAnalysisService, FeedbackDetailsWithResultIdsDTO, SimplifiedTask } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { AlertService } from 'app/core/util/alert.service';

export interface FeedbackDetail {
    count: number;
    relativeCount: number;
    detailText: string;
    testCaseName: string;
    task: number;
}

@Component({
    selector: 'jhi-feedback-analysis',
    templateUrl: './feedback-analysis.component.html',
    standalone: true,
    imports: [ArtemisSharedModule],
    providers: [FeedbackAnalysisService],
})
export class FeedbackAnalysisComponent implements OnInit {
    @Input() exerciseTitle?: string;
    @Input() exerciseId?: number;
    @Input() isAtLeastEditor!: undefined | boolean;
    resultIds: number[] = [];
    tasks: SimplifiedTask[] = [];
    feedbackDetails: FeedbackDetail[] = [];

    constructor(
        private simplifiedProgrammingExerciseTaskService: FeedbackAnalysisService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        if (this.isAtLeastEditor) {
            if (this.exerciseId) {
                this.loadTasks(this.exerciseId)
                    .pipe(concatMap(() => this.loadFeedbackDetails(this.exerciseId!)))
                    .subscribe();
            }
        } else {
            this.alertService.error('Permission Denied');
        }
    }

    loadTasks(exerciseId: number): Observable<SimplifiedTask[]> {
        return this.simplifiedProgrammingExerciseTaskService.getSimplifiedTasks(exerciseId).pipe(
            tap((tasks) => {
                this.tasks = tasks;
            }),
        );
    }

    loadFeedbackDetails(exerciseId: number): Observable<HttpResponse<FeedbackDetailsWithResultIdsDTO>> {
        return this.simplifiedProgrammingExerciseTaskService.getFeedbackDetailsForExercise(exerciseId).pipe(
            tap((response) => {
                this.resultIds = response.body?.resultIds || [];
                const feedbackDetails = response.body?.feedbackDetails || [];
                this.saveFeedback(feedbackDetails);
            }),
        );
    }

    saveFeedback(feedbackDetails: FeedbackDetail[]): void {
        const feedbackMap: Map<string, FeedbackDetail> = new Map();

        feedbackDetails.forEach((feedback) => {
            const feedbackText = feedback.detailText ?? '';
            const testCaseName = feedback.testCaseName ?? '';
            const key = `${feedbackText}_${testCaseName}`;

            const existingFeedback = feedbackMap.get(key);
            if (existingFeedback) {
                existingFeedback.count += 1;
                existingFeedback.relativeCount = this.getRelativeCount(existingFeedback.count);
            } else {
                const task = this.taskIndex(testCaseName);
                feedbackMap.set(key, {
                    count: 1,
                    relativeCount: this.getRelativeCount(1),
                    detailText: feedbackText,
                    testCaseName: testCaseName,
                    task: task,
                });
            }
        });
        this.feedbackDetails = Array.from(feedbackMap.values()).sort((a, b) => b.count - a.count);
    }

    taskIndex(testCaseName: string): number {
        if (!testCaseName) {
            return 0;
        }
        return this.tasks.findIndex((tasks) => tasks.testCases?.some((testCase) => testCase.testName === testCaseName)) + 1;
    }

    getRelativeCount(count: number): number {
        return (count / this.resultIds.length) * 100;
    }
}
