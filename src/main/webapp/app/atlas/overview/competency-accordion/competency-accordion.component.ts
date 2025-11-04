import { Component, OnChanges, SimpleChanges, inject, input, output } from '@angular/core';
import { faFile, faFilePdf, faList } from '@fortawesome/free-solid-svg-icons';
import { MIN_SCORE_GREEN } from 'app/app.constants';
import { Competency, CompetencyJol, CompetencyProgress, getConfidence, getIcon, getMastery, getProgress } from 'app/atlas/shared/entities/competency.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Router, RouterLink } from '@angular/router';
import { CompetencyInformation, LectureUnitInformation, StudentMetrics } from 'app/atlas/shared/entities/student-metrics.model';
import { round } from 'app/shared/util/utils';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { LectureUnitType, lectureUnitIcons, lectureUnitTooltips } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { isStartPracticeAvailable } from 'app/exercise/util/exercise.utils';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbProgressbar, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyRingsComponent } from 'app/atlas/shared/competency-rings/competency-rings.component';
import { JudgementOfLearningRatingComponent } from 'app/atlas/overview/judgement-of-learning-rating/judgement-of-learning-rating.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseExerciseRowComponent } from 'app/core/course/overview/course-exercises/course-exercise-row/course-exercise-row.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

export interface CompetencyAccordionToggleEvent {
    opened: boolean;
    index: number;
}

@Component({
    selector: 'jhi-competency-accordion',
    templateUrl: './competency-accordion.component.html',
    styleUrl: './competency-accordion.component.scss',
    imports: [
        FaIconComponent,
        NgbTooltip,
        NgbProgressbar,
        CompetencyRingsComponent,
        JudgementOfLearningRatingComponent,
        TranslateDirective,
        CourseExerciseRowComponent,
        RouterLink,
        ArtemisTranslatePipe,
    ],
})
export class CompetencyAccordionComponent implements OnChanges {
    private router = inject(Router);

    course = input<Course>();
    competency = input.required<CompetencyInformation>();
    metrics = input.required<StudentMetrics>();
    index = input.required<number>();
    openedIndex = input<number>();

    accordionToggle = output<CompetencyAccordionToggleEvent>();

    open = false;
    nextExercises: Exercise[] = [];
    nextLectureUnits: LectureUnitInformation[] = [];
    exercisesProgress?: number;
    lectureUnitsProgress?: number;
    confidence: number = 1;
    mastery: number = 0;
    progress: number = 0;
    jolRating?: number;
    promptForRating = false;

    protected readonly faList = faList;
    protected readonly faFile = faFile;
    protected readonly faFilePdf = faFilePdf;
    protected readonly lectureUnitIcons = lectureUnitIcons;
    protected readonly lectureUnitTooltips = lectureUnitTooltips;
    protected readonly LectureUnitType = LectureUnitType;
    protected readonly getIcon = getIcon;
    protected readonly getProgress = getProgress;
    protected readonly getConfidence = getConfidence;
    protected readonly getMastery = getMastery;
    protected readonly round = round;

    ngOnChanges(changes: SimpleChanges) {
        if (changes.openedIndex && this.index() !== this.openedIndex()) {
            this.open = false;
        }
        if (changes.metrics) {
            this.setNextExercises();
            this.setNextLessonUnits();
            this.calculateProgressValues();

            const courseCompetencies = Object.values(this.metrics().competencyMetrics?.competencyInformation ?? {}).map((competency) => {
                return {
                    ...competency,
                    userProgress: [
                        {
                            progress: this.metrics().competencyMetrics?.progress?.[competency.id] ?? 0,
                            confidence: this.metrics().competencyMetrics?.confidence?.[competency.id] ?? 1,
                        },
                    ],
                } satisfies Competency;
            });
            this.promptForRating = CompetencyJol.shouldPromptForJol(
                this.competency() satisfies Competency,
                {
                    progress: this.metrics().competencyMetrics?.progress?.[this.competency().id],
                    confidence: this.metrics().competencyMetrics?.confidence?.[this.competency().id],
                },
                courseCompetencies,
            );
        }
    }

    setNextExercises() {
        if (!this.metrics()) {
            this.nextExercises = [];
            return;
        }
        const courseExercises = this.course()?.exercises ?? [];
        const exerciseIdToExercise = Object.fromEntries(courseExercises.map((exercise) => [exercise.id, exercise] as [number, Exercise]));
        const activeCompetencyExercises = (this.metrics().competencyMetrics?.exercises?.[this.competency().id] ?? [])
            .flatMap((exerciseId) => [exerciseIdToExercise[exerciseId]])
            .filter((exercise) => exercise.releaseDate?.isBefore(dayjs()))
            .filter((exercise) => exercise.dueDate?.isAfter(dayjs()) || isStartPracticeAvailable(exercise));

        const exerciseIdToMaxScore = Object.fromEntries(
            activeCompetencyExercises.map((exercise) => {
                const score =
                    exercise.studentParticipations
                        ?.flatMap((participation) => participation.submissions?.flatMap((submission) => submission.results ?? []) ?? [])
                        .reduce((max, result) => Math.max(max, result.score ?? 0), -1) ?? 0;
                return [exercise.id, score] as [number, number];
            }),
        );

        const completionThreshold = MIN_SCORE_GREEN;
        this.nextExercises = activeCompetencyExercises
            .filter((exercise) => exercise.id && exerciseIdToMaxScore[exercise.id] <= completionThreshold)
            .sort((a, b) => {
                const scoreA = a.id ? (exerciseIdToMaxScore[a.id] ?? 0) : 0;
                const scoreB = b.id ? (exerciseIdToMaxScore[b.id] ?? 0) : 0;

                if (scoreA !== scoreB) {
                    return scoreA - scoreB;
                }
                const dueDateA = a.dueDate ?? a.startDate;
                const dueDateB = b.dueDate ?? b.startDate;
                return dueDateA?.diff(dueDateB) ?? 0;
            })
            .slice(0, 5);
    }

    setNextLessonUnits() {
        if (!this.metrics()) {
            this.nextLectureUnits = [];
            return;
        }
        const completedLectureUnits = this.metrics().lectureUnitStudentMetricsDTO?.completed ?? [];
        const competencyLectureUnits = this.metrics().competencyMetrics?.lectureUnits?.[this.competency().id] ?? [];
        this.nextLectureUnits = competencyLectureUnits
            .filter((lectureUnitId) => !completedLectureUnits.includes(lectureUnitId))
            .flatMap((lectureUnitId) => this.metrics().lectureUnitStudentMetricsDTO?.lectureUnitInformation?.[lectureUnitId] ?? [])
            .filter((lectureUnit) => lectureUnit.releaseDate?.isBefore(dayjs()))
            .sort((a, b) => (a.releaseDate?.isBefore(b?.releaseDate) ? -1 : 1))
            .slice(0, Math.max(0, 5 - this.nextExercises.length));
    }

    calculateProgressValues() {
        const jol = this.metrics().competencyMetrics?.currentJolValues?.[this.competency().id];
        this.jolRating = jol?.jolValue;
        this.exercisesProgress = this.calculateExercisesProgress();
        this.lectureUnitsProgress = this.calculateLectureUnitsProgress();
        const userProgress = this.getUserProgress();
        if (this.jolRating !== undefined) {
            this.progress = this.getProgress(userProgress);
            this.confidence = this.getConfidence(userProgress);
            this.mastery = this.getMastery(userProgress);
        } else {
            this.progress = 0;
            this.confidence = 1;
            this.mastery = 0;
        }
    }

    calculateExercisesProgress(): number | undefined {
        if (!this.metrics().exerciseMetrics) {
            return undefined;
        }

        const competencyExercises = this.metrics().competencyMetrics?.exercises?.[this.competency().id];

        if (competencyExercises === undefined || competencyExercises.length === 0) {
            return undefined;
        }

        const competencyPoints = competencyExercises
            ?.map(
                (exercise) => ((this.metrics().exerciseMetrics?.score?.[exercise] ?? 0) * (this.metrics().exerciseMetrics?.exerciseInformation?.[exercise]?.maxPoints ?? 0)) / 100,
            )
            .reduce((a, b) => a + b, 0);
        const competencyMaxPoints = competencyExercises
            ?.map((exercise) => this.metrics().exerciseMetrics?.exerciseInformation?.[exercise]?.maxPoints ?? 0)
            .reduce((a, b) => a + b, 0);

        if (!competencyMaxPoints) {
            return undefined;
        }
        return round((competencyPoints / competencyMaxPoints) * 100, 1);
    }

    calculateLectureUnitsProgress(): number | undefined {
        if (!this.metrics().lectureUnitStudentMetricsDTO) {
            return undefined;
        }

        const competencyLectureUnits = this.metrics().competencyMetrics?.lectureUnits?.[this.competency().id];
        const releasedLectureUnits = competencyLectureUnits?.filter((lectureUnitId) =>
            this.metrics().lectureUnitStudentMetricsDTO?.lectureUnitInformation?.[lectureUnitId]?.releaseDate?.isBefore(dayjs()),
        );
        if (releasedLectureUnits === undefined || releasedLectureUnits.length === 0) {
            return undefined;
        }

        const completedLectureUnits = releasedLectureUnits?.filter((lectureUnitId) => this.metrics().lectureUnitStudentMetricsDTO?.completed?.includes(lectureUnitId)).length ?? 0;
        const progress = (completedLectureUnits / releasedLectureUnits.length) * 100;
        return round(progress, 1);
    }

    getUserProgress(): CompetencyProgress {
        const progress = this.metrics().competencyMetrics?.progress?.[this.competency().id] ?? 0;
        const confidence = this.metrics().competencyMetrics?.confidence?.[this.competency().id] ?? 1;
        return { progress, confidence };
    }

    toggle() {
        this.open = !this.open;
        this.accordionToggle.emit({ opened: this.open, index: this.index() });
    }

    onRatingChange(newRating: number) {
        const competencyMetrics = this.metrics().competencyMetrics;
        if (competencyMetrics) {
            competencyMetrics.currentJolValues = {
                ...(competencyMetrics.currentJolValues ?? {}),
                [this.competency().id]: {
                    competencyId: this.competency().id,
                    jolValue: newRating,
                    judgementTime: dayjs().toString(),
                    competencyProgress: this.progress,
                    competencyConfidence: this.confidence,
                },
            };
            this.calculateProgressValues();
        }
    }

    navigateToCompetencyDetailPage(event: Event) {
        event.stopPropagation();
        const course = this.course();
        if (!course?.id) {
            return;
        }
        this.router.navigate(['/courses', course.id, 'competencies', this.competency().id]);
    }
}
