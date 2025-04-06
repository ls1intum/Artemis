import { Component, OnInit, inject } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ActivatedRoute } from '@angular/router';
import { ExerciseFilter } from 'app/exercise/shared/entities/exercise/exercise-filter.model';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { CourseManagementExercisesSearchComponent } from '../exercises-search/course-management-exercises-search.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseExerciseCardComponent } from '../course-exercise-card/course-exercise-card.component';
import { ProgrammingExerciseCreateButtonsComponent } from 'app/programming/manage/programming-exercise-create-buttons.component';
import { ProgrammingExerciseComponent } from 'app/programming/manage/programming-exercise.component';
import { QuizExerciseCreateButtonsComponent } from 'app/quiz/manage/quiz-exercise-create-buttons.component';
import { QuizExerciseComponent } from 'app/quiz/manage/quiz-exercise.component';
import { ExerciseCreateButtonsComponent } from 'app/exercise/manage/exercise-create-buttons.component';
import { ModelingExerciseComponent } from 'app/modeling/manage/modeling-exercise.component';
import { TextExerciseComponent } from 'app/text/manage/text-exercise/text-exercise.component';
import { FileUploadExerciseComponent } from 'app/fileupload/manage/file-upload-exercise.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-course-management-exercises',
    templateUrl: './course-management-exercises.component.html',
    imports: [
        DocumentationButtonComponent,
        CourseManagementExercisesSearchComponent,
        TranslateDirective,
        CourseExerciseCardComponent,
        ProgrammingExerciseCreateButtonsComponent,
        ProgrammingExerciseComponent,
        QuizExerciseCreateButtonsComponent,
        QuizExerciseComponent,
        ExerciseCreateButtonsComponent,
        ModelingExerciseComponent,
        TextExerciseComponent,
        FileUploadExerciseComponent,
        ArtemisTranslatePipe,
    ],
})
export class CourseManagementExercisesComponent implements OnInit {
    readonly ExerciseType = ExerciseType;
    readonly documentationType: DocumentationType = 'Exercise';

    course: Course;
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
    exerciseFilter: ExerciseFilter;

    private readonly route = inject(ActivatedRoute);

    /**
     * initializes course
     */
    ngOnInit(): void {
        this.route.parent!.data.subscribe(({ course }) => {
            if (course) {
                this.course = course;
            }
        });

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
