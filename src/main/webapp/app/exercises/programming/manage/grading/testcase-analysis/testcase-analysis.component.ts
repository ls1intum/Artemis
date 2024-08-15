import { Component, Input, OnInit } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Participation } from 'app/entities/participation/participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';

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
        if (this.programmingExerciseTaskService.exercise.id != undefined) {
            // Find all participations for the programming exercise and instantiate the feedbacks array with the FeedbackDetail structure
            this.participationService.findAllParticipationsByExercise(this.programmingExerciseTaskService.exercise.id, true).subscribe((participationsResponse) => {
                this.participation = participationsResponse.body ?? [];
                this.loadFeedbacks(this.participation);
            });
        }
        // Load tasks for the programming exercise
        this.tasks = this.programmingExerciseTaskService.updateTasks();
    }

    loadFeedbacks(participations: Participation[]): void {
        // Iterate over all participations, get feedback details for each result, and filter them for negative feedback
        participations.forEach((participation) => {
            participation.results?.forEach((result) => {
                this.resultService.getFeedbackDetailsForResult(participation.id!, result).subscribe((response) => {
                    const feedbackArray = response.body ?? [];
                    const negativeFeedbackArray = feedbackArray.filter((feedback) => !feedback.positive); // Filter out positive feedback
                    this.saveFeedbacks(negativeFeedbackArray); // Save only negative feedback
                });
            });
        });
    }

    saveFeedbacks(feedbackArray: Feedback[]): void {
        // Iterate over all feedback and save them in the feedbacks array
        // If a feedback with the corresponding testcase already exists in the list, then the count is incremented; otherwise, a new FeedbackDetail is added
        feedbackArray.forEach((feedback) => {
            const feedbackText = feedback.detailText ?? '';
            const existingFeedback = this.feedbacks.find((f) => f.detailText === feedbackText && f.testcase === feedback.testCase?.testName);
            if (existingFeedback) {
                existingFeedback.count += 1; // Increment count if feedback already exists
            } else {
                const task = this.findTaskIndexForTestCase(feedback.testCase); // Find the task index for the test case
                this.feedbacks.push(<FeedbackDetail>{
                    count: 1,
                    detailText: feedback.detailText ?? '',
                    testcase: feedback.testCase?.testName,
                    task: task,
                });
            }
        });
        this.feedbacks.sort((a, b) => b.count - a.count); // Sort feedback by count in descending order
    }

    findTaskIndexForTestCase(testCase?: ProgrammingExerciseTestCase): number | undefined {
        if (!testCase) {
            return undefined;
        }
        // Find the index of the task and add 1 to it (to make it a 1-based index)
        return this.tasks.findIndex((task) => task.testCases.some((tc) => tc.testName === testCase.testName)) + 1;
    }

    // Used to calculate the relative occurrence of a feedback
    getRelativeCount(count: number): number {
        return (this.participation.length > 0 ? count / this.participation.length : 0) * 100;
    }
}
