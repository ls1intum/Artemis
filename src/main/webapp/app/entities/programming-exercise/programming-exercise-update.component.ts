import { ActivatedRoute } from '@angular/router';
import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { Observable, Subject } from 'rxjs';
import { Course } from 'app/entities/course';
import { CourseService } from 'app/entities/course/course.service';
import { ExerciseCategory, ExerciseService } from 'app/entities/exercise';
import { ProgrammingExercise, ProgrammingLanguage } from './programming-exercise.model';
import { ProgrammingExerciseService } from './services/programming-exercise.service';
import { FileService } from 'app/shared/http/file.service';
import { MAX_SCORE_PATTERN } from 'app/app.constants';
import { TranslateService } from '@ngx-translate/core';
import { switchMap, tap } from 'rxjs/operators';
import { KatexCommand } from 'app/markdown-editor/commands';
import { EditorMode } from 'app/markdown-editor';
import { AssessmentType } from 'app/entities/assessment-type';
import { FeatureToggle } from 'app/feature-toggle';

@Component({
    selector: 'jhi-programming-exercise-update',
    templateUrl: './programming-exercise-update.component.html',
    styleUrls: ['./programming-exercise-form.scss'],
})
export class ProgrammingExerciseUpdateComponent implements OnInit {
    FeatureToggle = FeatureToggle;
    readonly JAVA = ProgrammingLanguage.JAVA;
    readonly PYTHON = ProgrammingLanguage.PYTHON;
    readonly C = ProgrammingLanguage.C;

    private translationBasePath = 'artemisApp.programmingExercise.';

    submitButtonTitle: string;
    isImport: boolean;
    hashUnsavedChanges = false;
    programmingExercise: ProgrammingExercise;
    isSaving: boolean;
    problemStatementLoaded = false;
    templateParticipationResultLoaded = true;
    notificationText: string | null;
    domainCommandsGradingInstructions = [new KatexCommand()];
    EditorMode = EditorMode;
    AssessmentType = AssessmentType;
    rerenderSubject = new Subject<void>();
    // This is used to revert the select if the user cancels to override the new selected programming language.
    private selectedProgrammingLanguageValue: ProgrammingLanguage;

    maxScorePattern = MAX_SCORE_PATTERN;
    packageNamePattern = '^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+[0-9a-z_]$'; // package name must have at least 1 dot and must not start with a number
    shortNamePattern = '^[a-zA-Z][a-zA-Z0-9]*'; // must start with a letter and cannot contain special characters
    titleNamePattern = '^[a-zA-Z0-9-_ ]+'; // must only contain alphanumeric characters, or whitespaces, or '_' or '-'
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private exerciseService: ExerciseService,
        private fileService: FileService,
        private activatedRoute: ActivatedRoute,
        private translateService: TranslateService,
    ) {}

    /**
     * Will also trigger loading the corresponding programming exercise language template.
     *
     * @param language to change to.
     */
    set selectedProgrammingLanguage(language: ProgrammingLanguage) {
        this.selectedProgrammingLanguageValue = language;
        // Don't override the problem statement with the template in edit mode.
        if (this.programmingExercise.id === undefined) {
            this.loadProgrammingLanguageTemplate(language);
            // Rerender the instructions as the template has changed.
            this.rerenderSubject.next();
        }
    }

    get selectedProgrammingLanguage() {
        return this.selectedProgrammingLanguageValue;
    }

    ngOnInit() {
        this.isSaving = false;
        this.notificationText = null;
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
            this.selectedProgrammingLanguageValue = this.programmingExercise.programmingLanguage;
        });
        // If it is an import, just get the course, otherwise handle the edit and new cases
        this.activatedRoute.url
            .pipe(
                tap(segments => (this.isImport = segments.some(segment => segment.path === 'import'))),
                switchMap(() => this.activatedRoute.params),
                tap(params => {
                    if (this.isImport) {
                        const targetCourseId = params['courseId'];
                        this.isImport = true;
                        this.courseService.find(targetCourseId).subscribe(res => (this.programmingExercise.course = res.body!));

                        this.programmingExercise.dueDate = null;
                        this.programmingExercise.projectKey = null;
                        this.programmingExercise.buildAndTestStudentSubmissionsAfterDueDate = null;
                        this.programmingExercise.assessmentDueDate = null;
                        this.programmingExercise.releaseDate = null;
                        this.programmingExercise.shortName = '';
                        this.programmingExercise.title = '';
                    } else {
                        if (params['courseId']) {
                            const courseId = params['courseId'];
                            this.courseService.find(courseId).subscribe(res => {
                                const course = res.body!;
                                this.programmingExercise.course = course;
                                this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.programmingExercise);
                                this.courseService.findAllCategoriesOfCourse(this.programmingExercise.course.id).subscribe(
                                    (categoryRes: HttpResponse<string[]>) => {
                                        this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body!);
                                    },
                                    (categoryRes: HttpErrorResponse) => this.onError(categoryRes),
                                );
                            });
                        }
                    }

                    // Set submit button text depending on component state
                    if (this.isImport) {
                        this.submitButtonTitle = 'entity.action.import';
                    } else if (this.programmingExercise.id) {
                        this.submitButtonTitle = 'entity.action.save';
                    } else {
                        this.submitButtonTitle = 'entity.action.generate';
                    }
                }),
            )
            .subscribe();
        // If an exercise is created, load our readme template so the problemStatement is not empty
        this.selectedProgrammingLanguage = this.programmingExercise.programmingLanguage;
        if (this.programmingExercise.id !== undefined) {
            this.problemStatementLoaded = true;
        }
    }

    previousState() {
        window.history.back();
    }

    updateCategories(categories: ExerciseCategory[]) {
        this.programmingExercise.categories = categories.map(el => JSON.stringify(el));
    }

    save() {
        // If no release date is set, we warn the user.
        if (!this.programmingExercise.releaseDate) {
            const confirmNoReleaseDate = this.translateService.instant(this.translationBasePath + 'noReleaseDateWarning');
            if (!window.confirm(confirmNoReleaseDate)) {
                return;
            }
        }
        this.isSaving = true;
        if (this.isImport) {
            this.subscribeToSaveResponse(this.programmingExerciseService.importExercise(this.programmingExercise));
        } else if (this.programmingExercise.id !== undefined) {
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.subscribeToSaveResponse(this.programmingExerciseService.update(this.programmingExercise, requestOptions));
        } else {
            this.subscribeToSaveResponse(this.programmingExerciseService.automaticSetup(this.programmingExercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ProgrammingExercise>>) {
        result.subscribe(
            (res: HttpResponse<ProgrammingExercise>) => this.onSaveSuccess(),
            (res: HttpErrorResponse) => this.onSaveError(res),
        );
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError(error: HttpErrorResponse) {
        const errorMessage = error.headers.get('X-artemisApp-alert')!;
        // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
        const jhiAlert = this.jhiAlertService.error(errorMessage);
        jhiAlert.msg = errorMessage;
        this.isSaving = false;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    trackCourseById(index: number, item: Course) {
        return item.id;
    }

    /**
     * When setting the programming language, a change guard is triggered.
     * This is because we want to reload the instructions template for a different language, but don't want the user to loose unsaved changes.
     * If the user cancels the language will not be changed.
     *
     * @param language to switch to.
     */
    onProgrammingLanguageChange(language: ProgrammingLanguage) {
        // If there are unsaved changes and the user does not confirm, the language doesn't get changed
        if (this.hashUnsavedChanges) {
            const confirmLanguageChangeText = this.translateService.instant(this.translationBasePath + 'unsavedChangesLanguageChange');
            if (!window.confirm(confirmLanguageChangeText)) {
                return this.selectedProgrammingLanguage;
            }
        }
        this.selectedProgrammingLanguage = language;
        return language;
    }

    /**
     * Change the selected programming language for the current exercise. If there are unsaved changes, the user
     * will see a confirmation dialog about switching to a new template
     *
     * @param language The new programming language
     */
    private loadProgrammingLanguageTemplate(language: ProgrammingLanguage) {
        // Otherwise, just change the language and load the new template
        this.hashUnsavedChanges = false;
        this.problemStatementLoaded = false;
        this.programmingExercise.programmingLanguage = language;
        this.fileService.getTemplateFile('readme', this.programmingExercise.programmingLanguage).subscribe(
            file => {
                this.programmingExercise.problemStatement = file;
                this.problemStatementLoaded = true;
            },
            err => {
                this.programmingExercise.problemStatement = '';
                this.problemStatementLoaded = true;
                console.log('Error while getting template instruction file!', err);
            },
        );
    }
}
