import dayjs from 'dayjs/esm';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { faFilePdf, faList } from '@fortawesome/free-solid-svg-icons';
import { Competency, CompetencyProgress, getConfidence, getIcon, getMastery, getProgress } from 'app/entities/competency.model';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { Router } from '@angular/router';
import { ICompetencyAccordionToggleEvent } from 'app/shared/competency/interfaces/competency-accordion-toggle-event.interface';

@Component({
    selector: 'jhi-competency-accordion',
    templateUrl: './competency-accordion.component.html',
    styleUrl: './competency-accordion.component.scss',
})
export class CompetencyAccordionComponent implements OnChanges {
    @Input()
    course?: Course;
    @Input()
    competency: Competency;
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
        if (this.competency.userProgress?.length) {
            return this.competency.userProgress.first()!;
        }
        return { progress: 0, confidence: 0 } as CompetencyProgress;
    }

    get progress() {
        return this.getProgress(this.getUserProgress());
    }

    get lectureUnitsProgress() {
        if (this.competency.lectureUnits?.length) {
            const completedLectureUnits = this.competency.lectureUnits.filter((lectureUnit) => lectureUnit.completed).length;
            return Math.round((completedLectureUnits / this.competency.lectureUnits.length) * 100);
        }
        return 0;
    }

    get exercisesProgress() {
        if (this.competency.exercises?.length) {
            const completedExercises = this.competency.exercises.filter((exercise) => exercise.completed).length;
            return Math.round((completedExercises / this.competency.exercises.length) * 100);
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

    getNextExercise() {
        if (this.competency && this.competency.exercises && this.competency.exercises?.length !== 0) {
            this.remainingExercises = this.competency.exercises?.filter((exercise) => exercise && !exercise.completed && !this.isExerciseDueDayPassed(exercise));
            if (this.remainingExercises.length && this.remainingExercises.first()) {
                const exercise = this.remainingExercises.first();
                if (exercise?.dueDate) {
                    exercise.dueDate = dayjs(exercise.dueDate);
                }
                return exercise;
            }
            return null;
        }
        return null;
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

    get nextExercise() {
        return this.getNextExercise();
    }

    navigateToCompetencyDetailPage(event: Event) {
        event.stopPropagation();
        this.router.navigate(['/courses', this.course!.id, 'competencies', this.competency.id]);
    }
}
