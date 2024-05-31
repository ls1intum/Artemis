import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { faFilePdf, faList } from '@fortawesome/free-solid-svg-icons';
import { CompetencyProgress, getConfidence, getIcon, getMastery, getProgress } from 'app/entities/competency.model';
import { Course } from 'app/entities/course.model';
import { Router } from '@angular/router';
import { CompetencyInformation, LectureUnitInformation, StudentMetrics } from 'app/entities/student-metrics.model';
import { round } from 'app/shared/util/utils';
import { Exercise } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { lectureUnitIcons, lectureUnitTooltips } from 'app/entities/lecture-unit/lectureUnit.model';

export interface CompetencyAccordionToggleEvent {
    opened: boolean;
    index: number;
}

@Component({
    selector: 'jhi-competency-accordion',
    templateUrl: './competency-accordion.component.html',
    styleUrl: './competency-accordion.component.scss',
})
export class CompetencyAccordionComponent implements OnChanges {
    @Input() course: Course | undefined;
    @Input() competency: CompetencyInformation;
    @Input() metrics: StudentMetrics;
    @Input() index: number;
    @Input() openedIndex: number | undefined;

    @Output() accordionToggle = new EventEmitter<CompetencyAccordionToggleEvent>();

    open = false;
    nextExercises: Exercise[] = [];
    nextLectureUnits: LectureUnitInformation[] = [];
    exercisesProgress?: number;
    lectureUnitsProgress?: number;
    confidence: number = 0;
    mastery: number = 0;
    progress: number = 0;
    jolRating?: number;
    promptForRating = false;

    protected readonly faList = faList;
    protected readonly faPdf = faFilePdf;
    protected readonly lectureUnitIcons = lectureUnitIcons;
    protected readonly lectureUnitTooltips = lectureUnitTooltips;
    protected readonly getIcon = getIcon;
    protected readonly getProgress = getProgress;
    protected readonly getConfidence = getConfidence;
    protected readonly getMastery = getMastery;
    protected readonly round = round;

    constructor(private router: Router) {}

    ngOnChanges(changes: SimpleChanges) {
        if (changes.openedIndex && this.index !== this.openedIndex) {
            this.open = false;
        }
        if (changes.metrics) {
            this.setNextExercises();
            this.setNextLessonUnits();
            this.calculateProgressValues();

            const courseCompetencies = Object.values(this.metrics.competencyMetrics?.competencyInformation ?? {}).map((competency) => {
                return {
                    ...competency,
                    userProgress: [
                        {
                            progress: this.metrics.competencyMetrics?.progress?.[competency.id] ?? 0,
                            confidence: this.metrics.competencyMetrics?.confidence?.[competency.id] ?? 0,
                        },
                    ],
                } satisfies Competency;
            });
            this.promptForRating = CompetencyJol.shouldPromptForJol(
                this.competency satisfies Competency,
                {
                    progress: this.metrics.competencyMetrics?.progress?.[this.competency.id],
                    confidence: this.metrics.competencyMetrics?.confidence?.[this.competency.id],
                },
                courseCompetencies,
            );
        }
    }

    setNextExercises() {
        if (!this.metrics) {
            this.nextExercises = [];
        }

        const submittedExercises = Object.keys(this.metrics.exerciseMetrics?.latestSubmission ?? {}).map(Number);
        const competencyExercises = this.metrics.competencyMetrics?.exercises?.[this.competency.id] ?? [];
        const nextExerciseInformations = competencyExercises
            .filter((exerciseId) => !submittedExercises.includes(exerciseId))
            .flatMap((exerciseId) => this.metrics.exerciseMetrics?.exerciseInformation?.[exerciseId] ?? [])
            .filter((exercise) => exercise.startDate.isBefore(dayjs()) && (!exercise.dueDate || exercise.dueDate.isAfter(dayjs())))
            .sort((a, b) => (a.dueDate ?? a.startDate).diff(b.dueDate));

        // Workaround to convert ExerciseInformation to Exercise
        this.nextExercises = nextExerciseInformations.map(
            (exercise) =>
                ({
                    ...exercise,
                    studentAssignedTeamIdComputed: exercise.studentAssignedTeamId !== undefined,
                }) as unknown as Exercise,
        );
    }

    setNextLessonUnits() {
        if (!this.metrics) {
            this.nextLectureUnits = [];
        }

        const completedLectureUnits = this.metrics.lectureUnitStudentMetricsDTO?.completed ?? [];
        const competencyLectureUnits = this.metrics.competencyMetrics?.lectureUnits?.[this.competency.id] ?? [];
        this.nextLectureUnits = competencyLectureUnits
            .filter((lectureUnitId) => !completedLectureUnits.includes(lectureUnitId))
            .flatMap((lectureUnitId) => this.metrics.lectureUnitStudentMetricsDTO?.lectureUnitInformation?.[lectureUnitId] ?? [])
            .filter((lectureUnit) => lectureUnit.releaseDate?.isBefore(dayjs()))
            .sort((a, b) => (a.releaseDate?.isBefore(b?.releaseDate) ? -1 : 1));
    }

    calculateProgressValues() {
        const jol = this.metrics.competencyMetrics?.jolValues?.[this.competency.id];
        this.jolRating = jol?.jolValue;
        this.exercisesProgress = this.calculateExercisesProgress();
        this.lectureUnitsProgress = this.calculateLectureUnitsProgress();
        const userProgress = this.getUserProgress();
        if (this.jolRating !== undefined) {
            this.progress = this.getProgress(userProgress);
            this.confidence = this.getConfidence(userProgress, this.competency.masteryThreshold!);
            this.mastery = this.getMastery(userProgress, this.competency.masteryThreshold!);
        } else {
            this.progress = 0;
            this.confidence = 0;
            this.mastery = 0;
        }
    }

    calculateExercisesProgress(): number | undefined {
        if (!this.metrics.exerciseMetrics) {
            return undefined;
        }

        const competencyExercises = this.metrics.competencyMetrics?.exercises?.[this.competency.id];
        const completedExercises = competencyExercises?.filter((exerciseId) => this.metrics.exerciseMetrics?.completed?.includes(exerciseId)).length ?? 0;
        if (competencyExercises === undefined || competencyExercises.length === 0) {
            return undefined;
        }
        const progress = (completedExercises / competencyExercises.length) * 100;
        return round(progress, 1);
    }

    calculateLectureUnitsProgress(): number | undefined {
        if (!this.metrics.lectureUnitStudentMetricsDTO) {
            return undefined;
        }

        const competencyLectureUnits = this.metrics.competencyMetrics?.lectureUnits?.[this.competency.id];
        const releasedLectureUnits = competencyLectureUnits?.filter((lectureUnitId) =>
            this.metrics.lectureUnitStudentMetricsDTO?.lectureUnitInformation?.[lectureUnitId]?.releaseDate?.isBefore(dayjs()),
        );
        const completedLectureUnits = releasedLectureUnits?.filter((lectureUnitId) => this.metrics.lectureUnitStudentMetricsDTO?.completed?.includes(lectureUnitId)).length ?? 0;
        if (releasedLectureUnits === undefined || releasedLectureUnits.length === 0) {
            return undefined;
        }
        const progress = (completedLectureUnits / releasedLectureUnits.length) * 100;
        return round(progress, 1);
    }

    getUserProgress(): CompetencyProgress {
        const progress = this.metrics.competencyMetrics?.progress?.[this.competency.id] ?? 0;
        const confidence = this.metrics.competencyMetrics?.confidence?.[this.competency.id] ?? 0;
        return { progress, confidence };
    }

    toggle() {
        this.open = !this.open;
        this.accordionToggle.emit({ opened: this.open, index: this.index });
    }

    onRatingChange(newRating: number) {
        if (this.metrics.competencyMetrics) {
            this.metrics.competencyMetrics.jolValues = {
                ...this.metrics.competencyMetrics.jolValues,
                [this.competency.id]: {
                    competencyId: this.competency.id,
                    jolValue: newRating,
                    judgementTime: dayjs().toString(),
                },
            };
            this.calculateProgressValues();
        }
    }

    navigateToCompetencyDetailPage(event: Event) {
        event.stopPropagation();
        this.router.navigate(['/courses', this.course!.id, 'competencies', this.competency.id]);
    }
}
