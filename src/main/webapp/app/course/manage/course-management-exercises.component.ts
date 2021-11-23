import { Component, ContentChild, OnInit, TemplateRef } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from './course-management.service';
import { ActivatedRoute } from '@angular/router';
import { ExerciseFilter } from 'app/entities/exercise-filter.model';

@Component({
    selector: 'jhi-course-management-exercises',
    templateUrl: './course-management-exercises.component.html',
})
export class CourseManagementExercisesComponent implements OnInit {
    course: Course;
    courseId = 0;
    showSearch = false;
    quizExercisesCount = 0;
    textExercisesCount = 0;
    programmingExercisesCount = 0;
    modelingExercisesCount = 0;
    fileUploadExercisesCount = 0;
    filteredQuizExercisesCount = 0;
    filteredTextExercisesCount = 0;
    filteredProgrammingExercisesCount = 0;
    filteredModelingExercisesCount = 0;
    filteredFileUploadExercisesCount = 0;
    transformationExerciseCount = 0;
    exerciseFilter: ExerciseFilter;

    // extension points, see shared/extension-point
    @ContentChild('overrideProgrammingExerciseCard') overrideProgrammingExerciseCard: TemplateRef<any>;
    @ContentChild('overrideNonProgrammingExerciseCard') overrideNonProgrammingExerciseCard: TemplateRef<any>;

    constructor(private courseService: CourseManagementService, private route: ActivatedRoute) {}

    /**
     * initializes courseId and course
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.parent!.snapshot.paramMap.get('courseId'));
        this.courseService.find(this.courseId).subscribe((courseResponse) => (this.course = courseResponse.body!));
        this.exerciseFilter = new ExerciseFilter('');
    }

    /**
     * Sets the (filtered) programming exercise count. Required to pass a callback to the
     * overrideProgrammingExerciseCard extension since extensions don't support @Output
     * @param count to set the programmingExerciseCount to
     */
    setProgrammingExerciseCount(count: number) {
        this.programmingExercisesCount = count;
    }
    setFilteredProgrammingExerciseCount(count: number) {
        this.filteredProgrammingExercisesCount = count;
    }

    /**
     * Toggles the search bar
     */
    toggleSearch() {
        this.showSearch = !this.showSearch;
        // Reset the filter when the search bar is closed
        if (!this.showSearch) {
            this.exerciseFilter = new ExerciseFilter();
        }
    }

    getExerciseCount(): number {
        return this.quizExercisesCount + this.programmingExercisesCount + this.modelingExercisesCount + this.fileUploadExercisesCount + this.textExercisesCount;
    }

    getFilteredExerciseCount(): number {
        return (
            this.filteredProgrammingExercisesCount +
            this.filteredQuizExercisesCount +
            this.filteredModelingExercisesCount +
            this.filteredTextExercisesCount +
            this.filteredFileUploadExercisesCount
        );
    }

    shouldHideExerciseCard(type: string): boolean {
        return !['all', type].includes(this.exerciseFilter.exerciseTypeSearch);
    }
}
