import dayjs from 'dayjs/esm';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { faFilePdf, faList } from '@fortawesome/free-solid-svg-icons';
import { CompetencyProgress, getConfidence, getIcon, getMastery, getProgress } from 'app/entities/competency.model';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { Router } from '@angular/router';
import { ICompetencyAccordionToggleEvent } from 'app/shared/competency/interfaces/competency-accordion-toggle-event.interface';
import { CompetencyInformation, StudentMetrics } from 'app/entities/student-metrics.model';

@Component({
    selector: 'jhi-competency-accordion',
    templateUrl: './competency-accordion.component.html',
    styleUrl: './competency-accordion.component.scss',
})
export class CompetencyAccordionComponent implements OnChanges {
    @Input()
    course?: Course;
    @Input()
    competency: CompetencyInformation;
    @Input()
    metrics: StudentMetrics;
    @Input()
    index: number;
    @Input()
    openedIndex: number | null = null;

    @Output()
    accordionToggle = new EventEmitter<ICompetencyAccordionToggleEvent>();

    faList = faList;
    faPdf = faFilePdf;

    open = false;
    remainingExercises: Exercise[] = [];

    getIcon = getIcon;
    getProgress = getProgress;
    getConfidence = getConfidence;
    getMastery = getMastery;

    constructor(private router: Router) {}

    ngOnChanges(changes: SimpleChanges) {
        if (changes.openedIndex && this.index !== this.openedIndex) {
            this.open = false;
        }
    }
    toggle() {
        this.open = !this.open;
        this.accordionToggle.emit({ opened: this.open, index: this.index });
    }

    getUserProgress(): CompetencyProgress {
        const progress = this.metrics.competencyMetrics?.progress[this.competency.id] ?? 0;
        const confidence = this.metrics.competencyMetrics?.confidence[this.competency.id] ?? 0;
        return { progress, confidence } as CompetencyProgress;
    }

    get progress() {
        return this.getProgress(this.getUserProgress());
    }

    get lectureUnitsProgress() {
        if (this.metrics.lectureUnitStudentMetricsDTO) {
            const competencyLectureUnits = this.metrics.competencyMetrics?.lectureUnits[this.competency.id];
            const completedLectureUnits = competencyLectureUnits?.filter((lectureUnitId) => this.metrics.lectureUnitStudentMetricsDTO?.completed?.includes(lectureUnitId)).length;
            if (competencyLectureUnits && completedLectureUnits) {
                return Math.round((completedLectureUnits / competencyLectureUnits.length) * 100);
            }
            return 0;
        }
        return 0;
    }

    get exercisesProgress() {
        const competencyExercises = this.metrics.competencyMetrics?.exercises[this.competency.id];
        const completedExercises = competencyExercises?.filter((exerciseId) => this.metrics.exerciseMetrics?.completed?.includes(exerciseId)).length;
        if (competencyExercises && completedExercises) {
            return Math.round((completedExercises / competencyExercises.length) * 100);
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

    get competencySoftDueDayPassed() {
        return this.competency?.softDueDate && dayjs().isAfter(this.competency.softDueDate);
    }

    isExerciseDueDayPassed(exercise: Exercise): boolean {
        if (!exercise.dueDate) {
            return false;
        }
        return exercise.dueDate && dayjs().isAfter(exercise.dueDate);
    }

    navigateToCompetencyDetailPage(event: Event) {
        event.stopPropagation();
        this.router.navigate(['/courses', this.course!.id, 'competencies', this.competency.id]);
    }
}
