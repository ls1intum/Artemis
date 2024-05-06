import { Component, Input, OnInit } from '@angular/core';
import { faFilePdf, faList } from '@fortawesome/free-solid-svg-icons';
import { Competency, CompetencyProgress, getConfidence, getIcon, getMastery, getProgress } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-competency-accordion',
    templateUrl: './competency-accordion.component.html',
    styleUrl: './competency-accordion.component.scss',
})
export class CompetencyAccordionComponent {
    @Input()
    courseId?: number;
    @Input()
    competency: Competency;

    faList = faList;
    faPdf = faFilePdf;

    open = false;
    rated = false;
    masteryRating = 0;
    ratingAvailable = false;

    getIcon = getIcon;
    getProgress = getProgress;
    getConfidence = getConfidence;
    getMastery = getMastery;

    constructor() {}

    toggle() {
        this.open = !this.open;
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
            const completedExercises = 0; // this.competency.exercises.filter(exercise => exercise.completed).length;
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

    onRatingUpdated(rating: number) {
        this.masteryRating = rating;
        this.rated = true;
    }
}
