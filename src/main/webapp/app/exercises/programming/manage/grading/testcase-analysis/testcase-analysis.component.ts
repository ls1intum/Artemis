import { Component, Input, OnInit } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FeedbackDetailsWithResultIdsDTO, SimplifiedTask, TestcaseAnalysisService } from 'app/exercises/programming/manage/grading/testcase-analysis/testcase-analysis.service';
import { Observable } from 'rxjs';
import { concatMap, tap } from 'rxjs/operators';
import { HttpResponse } from '@angular/common/http';

type FeedbackDetail = {
    count: number;
    detailText: string;
    testcase: string;
    task: number;
};

@Component({
    selector: 'jhi-testcase-analysis',
    templateUrl: './testcase-analysis.component.html',
    standalone: true,
    imports: [ArtemisSharedModule],
    providers: [TestcaseAnalysisService],
})
export class TestcaseAnalysisComponent implements OnInit {
    @Input() exerciseTitle?: string;
    @Input() exerciseId?: number;
    resultIds: number[] = [];
    tasks: SimplifiedTask[] = [];
    feedback: FeedbackDetail[] = [];

    constructor(private simplifiedProgrammingExerciseTaskService: TestcaseAnalysisService) {}

    ngOnInit(): void {
        if (this.exerciseId) {
            this.loadTasks(this.exerciseId)
                .pipe(concatMap(() => this.loadFeedbackDetails(this.exerciseId!)))
                .subscribe();
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
                const feedbackArray = response.body?.feedbackDetails || [];
                this.saveFeedback(feedbackArray);
            }),
        );
    }

    saveFeedback(feedbackArray: { detailText: string; testCaseName: string }[]): void {
        const feedbackMap: Map<string, FeedbackDetail> = new Map();

        feedbackArray.forEach((feedback) => {
            const feedbackText = feedback.detailText ?? '';
            const testcase = feedback.testCaseName ?? '';
            const key = `${feedbackText}_${testcase}`;

            const existingFeedback = feedbackMap.get(key);
            if (existingFeedback) {
                existingFeedback.count += 1;
            } else {
                const task = this.taskIndex(testcase);
                feedbackMap.set(key, {
                    count: 1,
                    detailText: feedbackText,
                    testcase: testcase,
                    task: task,
                });
            }
        });
        this.feedback = Array.from(feedbackMap.values()).sort((a, b) => b.count - a.count);
    }

    taskIndex(testCaseName: string): number {
        if (!testCaseName) {
            return 0;
        }
        return this.tasks.findIndex((tasks) => tasks.testCases?.some((tc) => tc.testName === testCaseName)) + 1;
    }
}
