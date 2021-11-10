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
    private _quizExercisesCount = 0;
    private _textExercisesCount = 0;
    private _programmingExercisesCount = 0;
    private _modelingExercisesCount = 0;
    private _fileUploadExercisesCount = 0;
    private _filteredQuizExercisesCount = 0;
    private _filteredTextExercisesCount = 0;
    private _filteredProgrammingExercisesCount = 0;
    private _filteredModelingExercisesCount = 0;
    private _filteredFileUploadExercisesCount = 0;
    private _exerciseFilter: ExerciseFilter;
    exerciseCount = 0;
    filteredExerciseCount = 0;
    showNoResultMessage = false;
    hideQuizExerciseCard = false;
    hideTextExerciseCard = false;
    hideProgrammingExerciseCard = false;
    hideModelingExerciseCard = false;
    hideFileUploadExerciseCard = false;

    // extension points, see shared/extension-point
    @ContentChild('overrideProgrammingExerciseCard') overrideProgrammingExerciseCard: TemplateRef<any>;
    @ContentChild('overrideNonProgrammingExerciseCard') overrideNonProgrammingExerciseCard: TemplateRef<any>;

    constructor(private courseService: CourseManagementService, private route: ActivatedRoute) {}

    get exerciseFilter(): ExerciseFilter {
        return this._exerciseFilter;
    }

    set exerciseFilter(value: ExerciseFilter) {
        this._exerciseFilter = value;
        this.updateFilter();
    }

    set filteredModelingExercisesCount(value: number) {
        this._filteredModelingExercisesCount = value;
        this.updateFilteredExerciseCount();
    }

    set filteredProgrammingExercisesCount(value: number) {
        this._filteredProgrammingExercisesCount = value;
        this.updateFilteredExerciseCount();
    }

    set filteredTextExercisesCount(value: number) {
        this._filteredTextExercisesCount = value;
        this.updateFilteredExerciseCount();
    }

    set filteredQuizExercisesCount(value: number) {
        this._filteredQuizExercisesCount = value;
        this.updateFilteredExerciseCount();
    }

    set filteredFileUploadExercisesCount(value: number) {
        this._filteredFileUploadExercisesCount = value;
        this.updateFilteredExerciseCount();
    }

    set fileUploadExercisesCount(value: number) {
        this._fileUploadExercisesCount = value;
        this.updateExerciseCount();
    }

    set modelingExercisesCount(value: number) {
        this._modelingExercisesCount = value;
        this.updateExerciseCount();
    }

    set programmingExercisesCount(value: number) {
        this._programmingExercisesCount = value;
        this.updateExerciseCount();
    }

    set textExercisesCount(value: number) {
        this._textExercisesCount = value;
        this.updateExerciseCount();
    }

    set quizExercisesCount(value: number) {
        this._quizExercisesCount = value;
        this.updateExerciseCount();
    }

    /**
     * initializes courseId and course
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.parent!.snapshot.paramMap.get('courseId'));
        this.courseService.find(this.courseId).subscribe((courseResponse) => (this.course = courseResponse.body!));
        this._exerciseFilter = new ExerciseFilter('');
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

    updateNoResultMessage() {
        this.showNoResultMessage = this.showSearch && this.filteredExerciseCount === 0 && !this._exerciseFilter.isEmpty();
    }

    updateExerciseCount() {
        this.exerciseCount = this._quizExercisesCount + this._programmingExercisesCount + this._modelingExercisesCount + this._fileUploadExercisesCount + this._textExercisesCount;
    }

    updateFilteredExerciseCount() {
        this.filteredExerciseCount =
            this._filteredProgrammingExercisesCount +
            this._filteredQuizExercisesCount +
            this._filteredModelingExercisesCount +
            this._filteredTextExercisesCount +
            this._filteredFileUploadExercisesCount;
        this.updateNoResultMessage();
    }

    updateFilter() {
        this.hideQuizExerciseCard = this.shouldHideExerciseCard('quiz');
        this.hideTextExerciseCard = this.shouldHideExerciseCard('text');
        this.hideProgrammingExerciseCard = this.shouldHideExerciseCard('programming');
        this.hideModelingExerciseCard = this.shouldHideExerciseCard('modeling');
        this.hideFileUploadExerciseCard = this.shouldHideExerciseCard('file-upload');
        this.updateNoResultMessage();
    }

    private shouldHideExerciseCard(type: string): boolean {
        return !['all', type].includes(this._exerciseFilter.exerciseTypeSearch);
    }
}
