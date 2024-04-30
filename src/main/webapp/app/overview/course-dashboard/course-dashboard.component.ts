import { Component, OnDestroy, OnInit } from '@angular/core';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { Subscription, forkJoin } from 'rxjs';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { CourseScores } from 'app/course/course-scores/course-scores';
import { ScoreType } from 'app/shared/constants/score-type.constants';
import { Competency } from 'app/entities/competency.model';
import { onError } from 'app/shared/util/global.utils';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import dayjs from 'dayjs/esm';
import { ScoresStorageService } from 'app/course/course-scores/scores-storage.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

@Component({
    selector: 'jhi-course-dashboard',
    templateUrl: './course-dashboard.component.html',
    styleUrl: './course-dashboard.component.scss',
})
export class CourseDashboardComponent implements OnInit, OnDestroy {
    courseId: number;
    exerciseId: number;
    isLoading = false;

    public currentRelativeScore: number = 0;
    public overallPoints: number = 0;
    public reachablePoints: number = 0;

    public competencies: Competency[] = [];
    private prerequisites: Competency[] = [];

    private paramSubscription?: Subscription;
    private courseUpdatesSubscription?: Subscription;
    private courseExercises: Exercise[] = [];
    public course?: Course;
    filteredExerciseIDs: number[];
    courseExercisesNotIncludedInScore: Exercise[] = [];
    public data: any;

    constructor(
        private courseStorageService: CourseStorageService,
        private translateService: TranslateService,
        private scoresStorageService: ScoresStorageService,
        private alertService: AlertService,
        private route: ActivatedRoute,
        private competencyService: CompetencyService,
    ) {}

    ngOnInit(): void {
        this.paramSubscription = this.route.parent?.parent?.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });
        this.setCourse(this.courseStorageService.getCourse(this.courseId));

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.setCourse(course);
        });
    }

    private onCourseLoad(): void {
        if (this.course?.exercises) {
            this.courseExercises = this.course.exercises;
            this.calculateAndFilterNotIncludedInScore();
            this.calculateMaxPoints();
            this.calculateReachablePoints();
            this.calculateCurrentRelativeScores();
        }
    }
    calculateAndFilterNotIncludedInScore() {
        this.courseExercisesNotIncludedInScore = this.courseExercises.filter((exercise) => exercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED);
        this.courseExercises = this.courseExercises.filter((exercise) => !this.courseExercisesNotIncludedInScore.includes(exercise));
        this.filteredExerciseIDs = this.courseExercisesNotIncludedInScore.map((exercise) => exercise.id!);
    }
    calculateMaxPoints() {
        this.overallPoints = this.retrieveTotalScoreByScoreType(ScoreType.ABSOLUTE_SCORE);
    }

    calculateReachablePoints() {
        this.reachablePoints = this.retrieveTotalScoreByScoreType(ScoreType.REACHABLE_POINTS);
        console.log('reachablePoints', this.reachablePoints);
    }

    calculateCurrentRelativeScores() {
        this.currentRelativeScore = this.retrieveTotalScoreByScoreType(ScoreType.CURRENT_RELATIVE_SCORE);
    }

    /**
     * Retrieves the score for an arbitrary score type for the total scores from the scores storage service. Scores are calculated in the server when fetching all courses.
     * @param scoreType which type of score should be retrieved from the store. Element of {'absoluteScore', 'maxPoints', 'currentRelativeScore', 'presentationScore', 'reachablePoints', 'relativeScore'}
     * @returns requested score value
     */
    private retrieveTotalScoreByScoreType(scoreType: ScoreType): number {
        const totalScores: CourseScores | undefined = this.scoresStorageService.getStoredTotalScores(this.courseId);
        return this.getScoreByScoreType(totalScores, scoreType);
    }

    // Retrieve the score for a specific ScoreType from the CourseScores object.
    // The MAX_POINTS and REACHABLE_POINTS belong to the course.
    // All other ScoreTypes inform about the student's personal score and are stored in the StudentScores object.
    private getScoreByScoreType(scores: CourseScores | undefined, scoreType: ScoreType): number {
        if (!scores) {
            return NaN;
        }
        switch (scoreType) {
            case ScoreType.MAX_POINTS:
                return scores.maxPoints;
            case ScoreType.REACHABLE_POINTS:
                return scores.reachablePoints;
            case ScoreType.ABSOLUTE_SCORE:
                return scores.studentScores.absoluteScore;
            case ScoreType.RELATIVE_SCORE:
                return scores.studentScores.relativeScore;
            case ScoreType.CURRENT_RELATIVE_SCORE:
                return scores.studentScores.currentRelativeScore;
            case ScoreType.PRESENTATION_SCORE:
                return scores.studentScores.presentationScore;
            case ScoreType.REACHABLE_PRESENTATION_POINTS:
                return scores.reachablePresentationPoints;
        }
    }
    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.courseUpdatesSubscription?.unsubscribe();
    }

    /**
     * Loads all prerequisites and competencies for the course
     */
    loadCompetencies() {
        this.isLoading = true;
        forkJoin([this.competencyService.getAllForCourse(this.courseId), this.competencyService.getAllPrerequisitesForCourse(this.courseId)]).subscribe({
            next: ([competencies, prerequisites]) => {
                this.competencies = competencies.body!;
                this.prerequisites = prerequisites.body!;
                // Also update the course, so we do not need to fetch again next time
                if (this.course) {
                    this.course.competencies = this.competencies;
                    this.course.prerequisites = this.prerequisites;
                }
                this.isLoading = false;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    get nonMasteredCompetencies(): Competency[] {
        return this.competencies.filter((competency) => {
            if (competency.userProgress?.length && competency.masteryThreshold) {
                return competency.userProgress.first()!.progress! < 100 || competency.userProgress.first()!.confidence! < competency.masteryThreshold!;
            }
            return false;
        });
    }

    get nextCompetency(): Competency | undefined {
        if (this.nonMasteredCompetencies.length > 0) {
            return this.nonMasteredCompetencies[0];
        }
        return this.competencies.at(-1);
    }

    get nextExercise() {
        return this.nextCompetency?.exercises?.find((exercise) => exercise.dueDate && exercise.dueDate.isAfter(dayjs()) && !this.filteredExerciseIDs.includes(exercise.id!));
    }

    get countCompetencies(): number {
        return this.competencies.length;
    }

    private setCourse(course?: Course) {
        this.course = course;
        this.onCourseLoad();
        // Note: this component is only shown if there are at least 1 competencies or at least 1 prerequisites, so if they do not exist, we load the data from the server
        if (this.course && ((this.course.competencies && this.course.competencies.length > 0) || (this.course.prerequisites && this.course.prerequisites.length > 0))) {
            this.competencies = this.course.competencies || [];
            this.prerequisites = this.course.prerequisites || [];
        } else {
            this.loadCompetencies();
        }
        this.data = {
            course: this.course,
        };
    }

    protected readonly FeatureToggle = FeatureToggle;
}
