import { Component, Input, OnInit } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { CourseExerciseStatisticsDTO } from 'app/exercises/shared/exercise/exercise-statistics-dto.model';

export enum ExerciseRowType {
    FUTURE = 'future',
    CURRENT = 'current',
    PAST = 'past',
}

// Title and the category list will be trimmed to this length
const TITLE_LENGTH = 30;

@Component({
    selector: 'jhi-course-management-exercise-row',
    templateUrl: './course-management-exercise-row.component.html',
    styleUrls: ['course-management-exercise-row.scss'],
})
export class CourseManagementExerciseRowComponent implements OnInit {
    @Input() course: Course;
    @Input() exercise: Exercise;
    @Input() statistic: CourseExerciseStatisticsDTO;
    @Input() rowType: ExerciseRowType;

    // Expose enums to the template
    exerciseType = ExerciseType;
    exerciseRowType = ExerciseRowType;
    leftoverAssessments = false;
    isTeamExercise: boolean;

    getIcon(type: ExerciseType | undefined) {
        switch (type) {
            case ExerciseType.PROGRAMMING:
                return 'keyboard';
            case ExerciseType.MODELING:
                return 'project-diagram';
            case ExerciseType.QUIZ:
                return 'check-double';
            case ExerciseType.TEXT:
                return 'font';
            default:
            case ExerciseType.FILE_UPLOAD:
                return 'file-upload';
        }
    }

    getIconTooltip(type: ExerciseType | undefined) {
        switch (type) {
            case ExerciseType.PROGRAMMING:
                return 'artemisApp.exercise.isProgramming';
            case ExerciseType.MODELING:
                return 'artemisApp.exercise.isModeling';
            case ExerciseType.QUIZ:
                return 'artemisApp.exercise.isQuiz';
            case ExerciseType.TEXT:
                return 'artemisApp.exercise.isText';
            default:
            case ExerciseType.FILE_UPLOAD:
                return 'artemisApp.exercise.isFileUpload';
        }
    }

    displayCategories = false;
    categories: string;
    displayTitle: string;

    constructor() {}

    ngOnInit() {
        this.displayTitle = this.exercise.title ?? '';
        this.isTeamExercise = this.exercise.teamMode ?? false;
        if (this.displayTitle.length > TITLE_LENGTH) {
            this.displayTitle = this.displayTitle.substring(0, TITLE_LENGTH - 3) + '...';
        }

        this.displayCategories = !!this.exercise.categories && this.exercise.categories.length > 0;
        if (this.displayCategories) {
            const parsedCategories = this.exercise.categories!.map((c) => JSON.parse(c));
            this.categories = parsedCategories.map((p) => p['category']).join(', ');
            if (this.categories.length > TITLE_LENGTH) {
                this.categories = this.categories.substring(0, TITLE_LENGTH - 3) + '...';
            }
        }
    }
}
