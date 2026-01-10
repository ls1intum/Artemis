import { Component, OnInit, TemplateRef, computed, inject, signal } from '@angular/core';
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

    readonly course = signal<Course | undefined>(undefined);
    readonly showSearch = signal(false);
    readonly quizExercisesCount = signal(0);
    readonly textExercisesCount = signal(0);
    readonly programmingExercisesCount = signal(0);
    readonly modelingExercisesCount = signal(0);
    readonly fileUploadExercisesCount = signal(0);
    readonly filteredQuizExercisesCount = signal(0);
    readonly filteredTextExercisesCount = signal(0);
    readonly filteredProgrammingExercisesCount = signal(0);
    readonly filteredModelingExercisesCount = signal(0);
    readonly filteredFileUploadExercisesCount = signal(0);
    readonly exerciseFilter = signal<ExerciseFilter>(new ExerciseFilter(''));

    readonly textExerciseEnabled = signal(false);
    readonly modelingExerciseEnabled = signal(false);
    readonly fileUploadExerciseEnabled = signal(false);

    private readonly route = inject(ActivatedRoute);
    private readonly profileService = inject(ProfileService);

    readonly exerciseCount = computed(
        () => this.quizExercisesCount() + this.programmingExercisesCount() + this.modelingExercisesCount() + this.fileUploadExercisesCount() + this.textExercisesCount(),
    );

    readonly filteredExerciseCount = computed(
        () =>
            this.filteredProgrammingExercisesCount() +
            this.filteredQuizExercisesCount() +
            this.filteredModelingExercisesCount() +
            this.filteredTextExercisesCount() +
            this.filteredFileUploadExercisesCount(),
    );

    /**
     * initializes course
     */
    ngOnInit(): void {
        this.route.parent!.data.subscribe(({ course }) => {
            if (course) {
                this.course.set(course);
            }
        });

        this.textExerciseEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_TEXT));
        this.modelingExerciseEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_MODELING));
        this.fileUploadExerciseEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_FILEUPLOAD));
    }
    /**
     * Toggles the search bar
     */
    toggleSearch() {
        this.showSearch.update((value) => !value);
        // Reset the filter when the search bar is closed
        if (!this.showSearch()) {
            this.exerciseFilter.set(new ExerciseFilter());
        }
    }

    shouldHideExerciseCard(type: string): boolean {
        return !['all', type].includes(this.exerciseFilter().exerciseTypeSearch);
    }
}
