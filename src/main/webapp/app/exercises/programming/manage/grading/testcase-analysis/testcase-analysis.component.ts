import { Component, Input, OnInit } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ArtemisSharedModule } from 'app/shared/shared.module';

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
})
export class TestcaseAnalysisComponent implements OnInit {
    @Input() exerciseTitle?: string;
    resultIds: number[] = [];
    tasks: ProgrammingExerciseTask[] = [];
    feedback: FeedbackDetail[] = [];

    constructor(
        private resultService: ResultService,
        private programmingExerciseTaskService: ProgrammingExerciseTaskService,
    ) {}

    ngOnInit(): void {
        const exerciseId = this.programmingExerciseTaskService.exercise?.id;
        if (exerciseId) {
            this.loadFeedbackDetails(exerciseId);
        }
        this.tasks = this.programmingExerciseTaskService.updateTasks();
    }

    loadFeedbackDetails(exerciseId: number): void {
        this.resultService.getFeedbackDetailsForExercise(exerciseId).subscribe((response) => {
            this.resultIds = response.body?.resultIds || [];
            const feedbackArray = response.body?.feedback || [];
            const negativeFeedbackArray = feedbackArray.filter((feedback) => !feedback.positive);
            this.saveFeedback(negativeFeedbackArray);
        });
    }

    saveFeedback(feedbackArray: Feedback[]): void {
        const feedbackMap: Map<string, FeedbackDetail> = new Map();

        feedbackArray.forEach((feedback) => {
            const feedbackText = feedback.detailText ?? '';
            const testcase = feedback.testCase?.testName ?? '';
            const key = `${feedbackText}_${testcase}`;

            const existingFeedback = feedbackMap.get(key);
            if (existingFeedback) {
                existingFeedback.count += 1;
            } else {
                const task = this.findTaskIndexForTestCase(feedback.testCase);
                feedbackMap.set(key, {
                    count: 1,
                    detailText: feedback.detailText ?? '',
                    testcase: feedback.testCase?.testName ?? '',
                    task: task,
                });
            }
        });
        this.feedback = Array.from(feedbackMap.values()).sort((a, b) => b.count - a.count);
    }

    findTaskIndexForTestCase(testCase?: ProgrammingExerciseTestCase): number {
        if (!testCase) {
            return -1;
        }
        return this.tasks.findIndex((task) => task.testCases.some((tc) => tc.testName === testCase.testName)) + 1;
    }
}
