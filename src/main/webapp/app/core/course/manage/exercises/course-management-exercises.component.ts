import { Component, OnInit, TemplateRef, inject } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ActivatedRoute } from '@angular/router';
import { ExerciseFilter } from 'app/exercise/shared/entities/exercise/exercise-filter.model';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseManagementExercisesSearchComponent } from '../exercises-search/course-management-exercises-search.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseExerciseCardComponent } from '../course-exercise-card/course-exercise-card.component';
import { ProgrammingExerciseComponent } from 'app/programming/manage/exercise/programming-exercise.component';
import { QuizExerciseCreateButtonsComponent } from 'app/quiz/manage/create-buttons/quiz-exercise-create-buttons.component';
import { QuizExerciseComponent } from 'app/quiz/manage/exercise/quiz-exercise.component';
import { ExerciseCreateButtonsComponent } from 'app/exercise/exercise-create-buttons/exercise-create-buttons.component';
import { ModelingExerciseComponent } from 'app/modeling/manage/modeling-exercise/modeling-exercise.component';
import { TextExerciseComponent } from 'app/text/manage/text-exercise/exercise/text-exercise.component';
import { FileUploadExerciseComponent } from 'app/fileupload/manage/file-upload-exercise/file-upload-exercise.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_FILEUPLOAD, MODULE_FEATURE_MODELING, MODULE_FEATURE_TEXT } from 'app/app.constants';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseTitleBarTitleDirective } from 'app/core/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarActionsDirective } from 'app/core/course/shared/directives/course-title-bar-actions.directive';

@Component({
    selector: 'jhi-course-management-exercises',
    templateUrl: './course-management-exercises.component.html',
    imports: [
        DocumentationButtonComponent,
        CourseManagementExercisesSearchComponent,
        TranslateDirective,
        CourseExerciseCardComponent,
        ProgrammingExerciseComponent,
        QuizExerciseCreateButtonsComponent,
        QuizExerciseComponent,
        ExerciseCreateButtonsComponent,
        ModelingExerciseComponent,
        TextExerciseComponent,
        FileUploadExerciseComponent,
        ArtemisTranslatePipe,
        CourseTitleBarTitleDirective,
        CourseTitleBarActionsDirective,
    ],
})
export class CourseManagementExercisesComponent implements OnInit {
    protected readonly ExerciseType = ExerciseType;
    protected readonly documentationType: DocumentationType = 'Exercise';
    protected readonly FeatureToggle = FeatureToggle;

    titleTitleTpl?: TemplateRef<any>;
    actionButtonsTpl?: TemplateRef<any>;
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

    textExerciseEnabled = false;
    modelingExerciseEnabled = false;
    fileUploadExerciseEnabled = false;

    private readonly route = inject(ActivatedRoute);
    private readonly profileService = inject(ProfileService);

    /**
     * initializes course
     */
    ngOnInit(): void {
        this.route.parent!.data.subscribe(({ course }) => {
            if (course) {
                this.course = course;
            }
        });

        this.textExerciseEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_TEXT);
        this.modelingExerciseEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_MODELING);
        this.fileUploadExerciseEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_FILEUPLOAD);
        this.exerciseFilter = new ExerciseFilter('');
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
