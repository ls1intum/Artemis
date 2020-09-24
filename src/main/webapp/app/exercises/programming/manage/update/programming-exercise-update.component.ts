import { ActivatedRoute, Params } from '@angular/router';
import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/alert/alert.service';
import { Observable, Subject } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from '../services/programming-exercise.service';
import { FileService } from 'app/shared/http/file.service';
import { MAX_SCORE_PATTERN } from 'app/app.constants';
import { TranslateService } from '@ngx-translate/core';
import { switchMap, tap } from 'rxjs/operators';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Exercise, ExerciseCategory } from 'app/entities/exercise.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingExerciseSimulationService } from 'app/exercises/programming/manage/services/programming-exercise-simulation.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';

@Component({
    selector: 'jhi-programming-exercise-update',
    templateUrl: './programming-exercise-update.component.html',
    styleUrls: ['../programming-exercise-form.scss'],
})
export class ProgrammingExerciseUpdateComponent implements OnInit {
    FeatureToggle = FeatureToggle;
    ProgrammingLanguage = ProgrammingLanguage;

    private translationBasePath = 'artemisApp.programmingExercise.';

    submitButtonTitle: string;
    isImport: boolean;
    isExamMode: boolean;
    hasUnsavedChanges = false;
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
    // Java package name Regex according to Java 14 JLS (https://docs.oracle.com/javase/specs/jls/se14/html/jls-7.html#jls-7.4.1),
    // with the restriction to a-z,A-Z,_ as "Java letter" and 0-9 as digits due to JavaScript/Browser Unicode character class limitations
    packageNamePattern =
        '^(?!.*(?:\\.|^)(?:abstract|continue|for|new|switch|assert|default|if|package|synchronized|boolean|do|goto|private|this|break|double|implements|protected|throw|byte|else|import|public|throws|case|enum|instanceof|return|transient|catch|extends|int|short|try|char|final|interface|static|void|class|finally|long|strictfp|volatile|const|float|native|super|while|_|true|false|null)(?:\\.|$))[A-Z_a-z][0-9A-Z_a-z]*(?:\\.[A-Z_a-z][0-9A-Z_a-z]*)*$';
    shortNamePattern = '^[a-zA-Z][a-zA-Z0-9]*'; // must start with a letter and cannot contain special characters
    titleNamePattern = '^[a-zA-Z0-9-_ ]+'; // must only contain alphanumeric characters, or whitespaces, or '_' or '-'
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];

    public inProductionEnvironment: boolean;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private courseService: CourseManagementService,
        private jhiAlertService: AlertService,
        private exerciseService: ExerciseService,
        private fileService: FileService,
        private activatedRoute: ActivatedRoute,
        private translateService: TranslateService,
        private profileService: ProfileService,
        private programmingExerciseSimulationService: ProgrammingExerciseSimulationService,
        private exerciseGroupService: ExerciseGroupService,
    ) {}

    /**
     * Will also trigger loading the corresponding programming exercise language template.
     *
     * @param language to change to.
     */
    set selectedProgrammingLanguage(language: ProgrammingLanguage) {
        this.selectedProgrammingLanguageValue = language;
        // If we switch to another language which does not support static code analysis we need to reset the option
        if (language !== ProgrammingLanguage.JAVA) {
            this.programmingExercise.staticCodeAnalysisEnabled = false;
        }
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

    /**
     * Sets the values for the creation/update of a programming exercise
     */
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
                tap((segments) => (this.isImport = segments.some((segment) => segment.path === 'import'))),
                switchMap(() => this.activatedRoute.params),
                tap((params) => {
                    if (this.isImport) {
                        this.createProgrammingExerciseForImport(params);
                    } else {
                        if (params['courseId'] && params['examId'] && params['groupId']) {
                            this.exerciseGroupService.find(params['courseId'], params['examId'], params['groupId']).subscribe((res) => {
                                this.isExamMode = true;
                                this.programmingExercise.exerciseGroup = res.body!;
                            });
                        } else if (params['courseId']) {
                            const courseId = params['courseId'];
                            this.courseService.find(courseId).subscribe((res) => {
                                this.isExamMode = false;
                                this.programmingExercise.course = res.body!;
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

        // Checks if the current environment is production
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.inProductionEnvironment = profileInfo.inProduction;
            }
        });
    }

    /**
     * Setups the programming exercise for import. The route determine whether the new exercise will be imported as an exam
     * or a normal exercise.
     *
     * @param params given by ActivatedRoute
     */
    private createProgrammingExerciseForImport(params: Params) {
        this.isImport = true;
        // The source exercise is injected via the Resolver. The route parameters determine the target exerciseGroup or course
        if (params['courseId'] && params['examId'] && params['groupId']) {
            this.exerciseGroupService.find(params['courseId'], params['examId'], params['groupId']).subscribe((res) => {
                this.programmingExercise.exerciseGroup = res.body!;
                // Set course to null if a normal exercise is imported
                this.programmingExercise.course = null;
            });
            this.isExamMode = true;
        } else if (params['courseId']) {
            this.courseService.find(params['courseId']).subscribe((res) => {
                this.programmingExercise.course = res.body!;
                // Set exerciseGroup to null if an exam exercise is imported
                this.programmingExercise.exerciseGroup = null;
            });
            this.isExamMode = false;
        }
        this.programmingExercise.dueDate = null;
        this.programmingExercise.projectKey = null;
        this.programmingExercise.buildAndTestStudentSubmissionsAfterDueDate = null;
        this.programmingExercise.assessmentDueDate = null;
        this.programmingExercise.releaseDate = null;
        this.programmingExercise.shortName = '';
        this.programmingExercise.title = '';
    }

    /**
     * If an user clicks on the back button the previous page should be loaded
     */
    previousState() {
        window.history.back();
    }

    /**
     * Updates the categories
     * @param categories which should be set
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.programmingExercise.categories = categories.map((el) => JSON.stringify(el));
    }

    /**
     * Saves the programming exercise with the provided input
     */
    save() {
        // If no release date is set, we warn the user.
        if (!this.programmingExercise.releaseDate && !this.isExamMode) {
            const confirmNoReleaseDate = this.translateService.instant(this.translationBasePath + 'noReleaseDateWarning');
            if (!window.confirm(confirmNoReleaseDate)) {
                return;
            }
        }

        Exercise.sanitize(this.programmingExercise);

        this.isSaving = true;

        if (this.isImport) {
            this.subscribeToSaveResponse(this.programmingExerciseService.importExercise(this.programmingExercise));
        } else if (this.programmingExercise.id !== undefined) {
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.subscribeToSaveResponse(this.programmingExerciseService.update(this.programmingExercise, requestOptions));
        } else if (this.programmingExercise.noVersionControlAndContinuousIntegrationAvailable) {
            // only for testing purposes(noVersionControlAndContinuousIntegrationAvailable)
            this.subscribeToSaveResponse(this.programmingExerciseSimulationService.automaticSetupWithoutConnectionToVCSandCI(this.programmingExercise));
        } else {
            this.subscribeToSaveResponse(this.programmingExerciseService.automaticSetup(this.programmingExercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ProgrammingExercise>>) {
        result.subscribe(
            () => this.onSaveSuccess(),
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
        window.scrollTo(0, 0);
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
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
        if (this.hasUnsavedChanges) {
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
        this.hasUnsavedChanges = false;
        this.problemStatementLoaded = false;
        this.programmingExercise.programmingLanguage = language;
        this.fileService.getTemplateFile('readme', this.programmingExercise.programmingLanguage).subscribe(
            (file) => {
                this.programmingExercise.problemStatement = file;
                this.problemStatementLoaded = true;
            },
            () => {
                this.programmingExercise.problemStatement = '';
                this.problemStatementLoaded = true;
            },
        );
    }
}
