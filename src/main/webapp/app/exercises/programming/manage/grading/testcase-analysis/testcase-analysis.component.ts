import { Component, Input, OnInit } from '@angular/core';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SimplifiedTask, TestcaseAnalysisService } from 'app/exercises/programming/manage/grading/testcase-analysis/testcase-analysis.service';
import { concatMap } from 'rxjs/operators';
import { tap } from 'rxjs/operators';

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

    constructor(
        private resultService: ResultService,
        private simplifiedProgrammingExerciseTaskService: TestcaseAnalysisService,
    ) {}

    ngOnInit(): void {
        if (this.exerciseId) {
            this.loadTasks(this.exerciseId)
                .pipe(concatMap(() => this.loadFeedbackDetails(this.exerciseId!)))
                .subscribe();
        }
    }

    private loadTasks(exerciseId: number) {
        return this.simplifiedProgrammingExerciseTaskService.getSimplifiedTasks(exerciseId).pipe(
            tap((tasks) => {
                this.tasks = tasks;
            }),
        );
    }

    private loadFeedbackDetails(exerciseId: number) {
        return this.resultService.getFeedbackDetailsForExercise(exerciseId).pipe(
            tap((response) => {
                this.resultIds = response.body?.resultIds || [];
                const feedbackArray = response.body?.feedbackDetails || [];
                this.saveFeedback(feedbackArray);
            }),
        );
    }

    private saveFeedback(feedbackArray: { detailText: string; testCaseName: string }[]): void {
        const feedbackMap: Map<string, FeedbackDetail> = new Map();

        feedbackArray.forEach((feedback) => {
            const feedbackText = feedback.detailText ?? '';
            const testcase = feedback.testCaseName ?? '';
            const key = `${feedbackText}_${testcase}`;

            const existingFeedback = feedbackMap.get(key);
            if (existingFeedback) {
                existingFeedback.count += 1;
            } else {
                const task = this.findTaskIndexForTestCase(testcase);
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

    private findTaskIndexForTestCase(testCaseName: string): number {
        if (!testCaseName) {
            return -1;
        }
        return this.tasks.findIndex((tasks) => tasks.testCases && tasks.testCases.some((tc) => tc.testName === testCaseName)) + 1;
    }
}
