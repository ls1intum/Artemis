import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { faFilePdf, faList } from '@fortawesome/free-solid-svg-icons';
import { CompetencyProgress, getConfidence, getIcon, getMastery, getProgress } from 'app/entities/competency.model';
import { Course } from 'app/entities/course.model';
import { Router } from '@angular/router';
import { ICompetencyAccordionToggleEvent } from 'app/shared/competency/interfaces/competency-accordion-toggle-event.interface';
import { CompetencyInformation, StudentMetrics } from 'app/entities/student-metrics.model';
import { round } from 'app/shared/util/utils';
import { Exercise } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-competency-accordion',
    templateUrl: './competency-accordion.component.html',
    styleUrl: './competency-accordion.component.scss',
})
export class CompetencyAccordionComponent implements OnChanges {
    @Input() course?: Course;
    @Input() competency: CompetencyInformation;
    @Input() metrics: StudentMetrics;
    @Input() index: number;
    @Input() openedIndex?: number;

    @Output() accordionToggle = new EventEmitter<ICompetencyAccordionToggleEvent>();

    open = false;

    nextExercises: Exercise[] = [];

    protected readonly faList = faList;
    protected readonly faPdf = faFilePdf;
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
            .filter((exercise) => exercise.startDate.isBefore(dayjs()) && exercise.dueDate.isAfter(dayjs()))
            .sort((a, b) => a.dueDate.diff(b.dueDate));

        this.nextExercises = nextExerciseInformations as Exercise[]; // TODO: Fix type casting
    }

    toggle() {
        this.open = !this.open;
        this.accordionToggle.emit({ opened: this.open, index: this.index });
    }

    getUserProgress(): CompetencyProgress {
        const progress = this.metrics.competencyMetrics?.progress?.[this.competency.id] ?? 0;
        const confidence = this.metrics.competencyMetrics?.confidence?.[this.competency.id] ?? 0;
        return { progress, confidence } as CompetencyProgress;
    }

    get progress() {
        return this.getProgress(this.getUserProgress());
    }

    get lectureUnitsProgress() {
        if (this.metrics.lectureUnitStudentMetricsDTO) {
            const competencyLectureUnits = this.metrics.competencyMetrics?.lectureUnits?.[this.competency.id];
            const completedLectureUnits = competencyLectureUnits?.filter((lectureUnitId) => this.metrics.lectureUnitStudentMetricsDTO?.completed?.includes(lectureUnitId)).length;
            if (competencyLectureUnits && completedLectureUnits) {
                const progress = (completedLectureUnits / competencyLectureUnits.length) * 100;
                return round(progress, 1);
            }
            return 0;
        }
        return 0;
    }

    get exercisesProgress() {
        const competencyExercises = this.metrics.competencyMetrics?.exercises?.[this.competency.id];
        const completedExercises = competencyExercises?.filter((exerciseId) => this.metrics.exerciseMetrics?.completed?.includes(exerciseId)).length;
        if (competencyExercises && completedExercises) {
            const progress = (completedExercises / competencyExercises.length) * 100;
            return round(progress, 1);
        }
        return 0;
    }

    get confidence() {
        return this.getConfidence(this.getUserProgress(), this.competency.masteryThreshold!);
    }

    get mastery() {
        return this.getMastery(this.getUserProgress(), this.competency.masteryThreshold!);
    }

    get competencyProgress() {
        return {
            progress: this.progress,
            confidence: this.confidence,
        } as CompetencyProgress;
    }

    navigateToCompetencyDetailPage(event: Event) {
        event.stopPropagation();
        this.router.navigate(['/courses', this.course!.id, 'competencies', this.competency.id]);
    }
}
