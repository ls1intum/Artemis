import { Component, Input, OnInit } from '@angular/core';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { forkJoin } from 'rxjs';
import { IndividualLearningGoalProgress } from 'app/course/learning-goals/learning-goal-individual-progress-dtos.model';
import { AccountService } from 'app/core/auth/account.service';
import { captureException } from '@sentry/browser';
import { isEqual } from 'lodash-es';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-course-learning-goals',
    templateUrl: './course-learning-goals.component.html',
    styles: [],
})
export class CourseLearningGoalsComponent implements OnInit {
    @Input()
    courseId: number;

    isLoading = false;
    course?: Course;
    learningGoals: LearningGoal[] = [];
    prerequisites: LearningGoal[] = [];
    learningGoalIdToLearningGoalProgress = new Map<number, IndividualLearningGoalProgress>();

    // this is calculated using the participant scores table on the server instead of going participation -> submission -> result
    // we calculate it here to find out if the participant scores table is robust enough to replace the classic way of finding the last result
    learningGoalIdToLearningGoalProgressUsingParticipantScoresTables = new Map<number, IndividualLearningGoalProgress>();

    constructor(
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
        private courseCalculationService: CourseScoreCalculationService,
        private learningGoalService: LearningGoalService,
        private accountService: AccountService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.parent?.parent?.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);
        if (this.course && this.course.learningGoals && this.course.prerequisites) {
            this.learningGoals = this.course.learningGoals;
            this.prerequisites = this.course.prerequisites;
            this.loadProgress();
        } else {
            this.loadData();
        }
    }

    getLearningGoalProgress(learningGoal: LearningGoal) {
        return this.learningGoalIdToLearningGoalProgress.get(learningGoal.id!);
    }

    /**
     * Loads all prerequisites and learning goals for the course
     */
    loadData() {
        this.isLoading = true;
        forkJoin([this.learningGoalService.getAllForCourse(this.courseId), this.learningGoalService.getAllPrerequisitesForCourse(this.courseId)]).subscribe({
            next: ([learningGoals, prerequisites]) => {
                this.learningGoals = learningGoals.body!;
                this.prerequisites = prerequisites.body!;
                this.loadProgress();
                this.isLoading = false;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    /**
     * Loads the respective progress for each learning goal
     */
    loadProgress() {
        if (!this.learningGoals) {
            return;
        }

        const progressObservable = this.learningGoals.map((lg) => {
            return this.learningGoalService.getProgress(lg.id!, this.courseId, false);
        });
        const progressObservableUsingParticipantScore = this.learningGoals.map((lg) => {
            return this.learningGoalService.getProgress(lg.id!, this.courseId, true);
        });

        this.isLoading = true;
        forkJoin([forkJoin(progressObservable), forkJoin(progressObservableUsingParticipantScore)]).subscribe({
            next: ([learningGoalProgressResponses, learningGoalProgressResponsesUsingParticipantScores]) => {
                for (const learningGoalProgressResponse of learningGoalProgressResponses) {
                    const learningGoalProgress = learningGoalProgressResponse.body!;
                    this.learningGoalIdToLearningGoalProgress.set(learningGoalProgress.learningGoalId, learningGoalProgress);
                }
                for (const learningGoalProgressResponse of learningGoalProgressResponsesUsingParticipantScores) {
                    const learningGoalProgress = learningGoalProgressResponse.body!;
                    this.learningGoalIdToLearningGoalProgressUsingParticipantScoresTables.set(learningGoalProgress.learningGoalId, learningGoalProgress);
                }
                this.isLoading = false;
                this.testIfScoreUsingParticipantScoresTableDiffers();
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    /**
     * Calculates a unique identity for each learning goal card shown in the component
     * @param index The index in the list
     * @param learningGoal The learning goal of the current iteration
     */
    identify(index: number, learningGoal: LearningGoal) {
        return `${index}-${learningGoal.id}`;
    }

    /**
     * Using this test we want to find out if the progress calculation using the participant scores table leads to the same
     * result as going through participation -> submission -> result
     */
    private testIfScoreUsingParticipantScoresTableDiffers() {
        this.learningGoalIdToLearningGoalProgress.forEach((learningGoalProgress, learningGoalId) => {
            const learningGoalProgressParticipantScoresTable = this.learningGoalIdToLearningGoalProgressUsingParticipantScoresTables.get(learningGoalId);
            if (!isEqual(learningGoalProgress.pointsAchievedByStudentInLearningGoal, learningGoalProgressParticipantScoresTable!.pointsAchievedByStudentInLearningGoal)) {
                const userName = this.accountService.userIdentity?.login;
                const message = `Warning: Learning Goal(id=${
                    learningGoalProgress.learningGoalId
                }) Progress different using participant scores for user ${userName}! Original: ${JSON.stringify(learningGoalProgress)} | Using ParticipantScores: ${JSON.stringify(
                    learningGoalProgressParticipantScoresTable,
                )}!`;
                captureException(new Error(message));
            }
        });
    }
}
