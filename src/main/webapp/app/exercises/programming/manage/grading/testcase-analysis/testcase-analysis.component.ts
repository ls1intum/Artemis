import { Component, Input, OnInit } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Participation } from 'app/entities/participation/participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { from, of } from 'rxjs';
import { catchError, mergeMap, toArray } from 'rxjs/operators';

// Define the structure for FeedbackDetail
type FeedbackDetail = {
    count: number;
    detailText: string;
    testcase: string;
    task: number;
};

@Component({
    selector: 'jhi-testcase-analysis',
    templateUrl: './testcase-analysis.component.html',
    styleUrls: ['./testcase-analysis.component.scss'],
})
export class TestcaseAnalysisComponent implements OnInit {
    @Input() exerciseTitle?: string;
    participation: Participation[] = [];
    tasks: ProgrammingExerciseTask[] = [];
    feedbacks: FeedbackDetail[] = [];

    constructor(
        private participationService: ParticipationService,
        private resultService: ResultService,
        private programmingExerciseTaskService: ProgrammingExerciseTaskService,
    ) {}

    ngOnInit(): void {
        const exerciseId = this.programmingExerciseTaskService.exercise?.id;
        if (exerciseId !== undefined) {
            // Find all participations for the programming exercise and instantiate the feedbacks array with the FeedbackDetail structure
            this.participationService.findAllParticipationsByExercise(exerciseId, true).subscribe((participationsResponse) => {
                this.participation = participationsResponse.body ?? [];
                this.loadFeedbacks(this.participation);
            });
        }
        // Load tasks for the programming exercise
        this.tasks = this.programmingExerciseTaskService.updateTasks();
    }

    // Iterate over all participations, get feedback details for each result, and filter them for negative feedback
    loadFeedbacks(participations: Participation[]): void {
        const MAX_CONCURRENT_REQUESTS = 5; // Maximum number of parallel requests

        from(participations)
            .pipe(
                mergeMap((participation) => {
                    return from(participation.results ?? []).pipe(
                        mergeMap((result) => {
                            return this.resultService.getFeedbackDetailsForResult(participation.id!, result).pipe(
                                catchError(() => {
                                    return of({ body: [] });
                                }),
                            );
                        }, MAX_CONCURRENT_REQUESTS),
                    );
                }, MAX_CONCURRENT_REQUESTS),
                toArray(),
            )
            .subscribe((responses) => {
                const feedbackArray = responses.flatMap((response) => response.body ?? []);
                const negativeFeedbackArray = feedbackArray.filter((feedback) => !feedback.positive); // Filter out positive feedback
                this.saveFeedbacks(negativeFeedbackArray); // Save only negative feedback
            });
    }

    // Iterate over all feedback and save them in the feedbacks array
    // If a feedback with the corresponding testcase already exists in the list, then the count is incremented; otherwise, a new FeedbackDetail is added
    saveFeedbacks(feedbackArray: Feedback[]): void {
        const feedbackMap: Map<string, FeedbackDetail> = new Map();

        feedbackArray.forEach((feedback) => {
            const feedbackText = feedback.detailText ?? '';
            const testcase = feedback.testCase?.testName ?? '';
            const key = `${feedbackText}_${testcase}`;

            if (feedbackMap.has(key)) {
                const existingFeedback = feedbackMap.get(key);
                if (existingFeedback) {
                    existingFeedback.count += 1;
                } // Increment count if feedback already exists
            } else {
                const task = this.findTaskIndexForTestCase(feedback.testCase); // Find the task index for the test case
                feedbackMap.set(key, <FeedbackDetail>{
                    count: 1,
                    detailText: feedback.detailText ?? '',
                    testcase: feedback.testCase?.testName,
                    task: task,
                });
            }
        });

        // Convert map values to array and sort feedback by count in descending order
        this.feedbacks = Array.from(feedbackMap.values()).sort((a, b) => b.count - a.count);
    }

    findTaskIndexForTestCase(testCase?: ProgrammingExerciseTestCase): number | undefined {
        if (!testCase) {
            return 0;
        }
        // Find the index of the task and add 1 to it (to make it a 1-based index), if 0 is returned then no element was found
        return this.tasks.findIndex((task) => task.testCases.some((tc) => tc.testName === testCase.testName)) + 1;
    }

    // Used to calculate the relative occurrence of a feedback
    getRelativeCount(count: number): number {
        return (this.participation.length > 0 ? count / this.participation.length : 0) * 100;
    }
}
