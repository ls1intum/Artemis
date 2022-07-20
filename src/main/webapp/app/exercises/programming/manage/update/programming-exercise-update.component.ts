import { ActivatedRoute, Params } from '@angular/router';
import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { Observable, Subject } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from '../services/programming-exercise.service';
import { FileService } from 'app/shared/http/file.service';
import { TranslateService } from '@ngx-translate/core';
import { switchMap, tap } from 'rxjs/operators';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Exercise, IncludedInOverallScore, resetDates, ValidationReason } from 'app/entities/exercise.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ProgrammingLanguageFeatureService } from 'app/exercises/programming/shared/service/programming-language-feature/programming-language-feature.service';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { shortNamePattern } from 'app/shared/constants/input.constants';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { cloneDeep } from 'lodash-es';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { onError } from 'app/shared/util/global.utils';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { faBan, faExclamationCircle, faQuestionCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { ModePickerOption } from 'app/exercises/shared/mode-picker/mode-picker.component';

@Component({
    selector: 'jhi-programming-exercise-update',
    templateUrl: './programming-exercise-update.component.html',
    styleUrls: ['../programming-exercise-form.scss'],
})
export class ProgrammingExerciseUpdateComponent implements OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    FeatureToggle = FeatureToggle;
    ProgrammingLanguage = ProgrammingLanguage;
    ProjectType = ProjectType;

    private translationBasePath = 'artemisApp.programmingExercise.';

    auxiliaryRepositoryDuplicateNames: boolean;
    auxiliaryRepositoryDuplicateDirectories: boolean;
    auxiliaryRepositoryNamedCorrectly: boolean;
    submitButtonTitle: string;
    isImport: boolean;
    isEdit: boolean;
    isExamMode: boolean;
    hasUnsavedChanges = false;
    programmingExercise: ProgrammingExercise;
    backupExercise: ProgrammingExercise;
    isSaving: boolean;
    problemStatementLoaded = false;
    templateParticipationResultLoaded = true;
    notificationText?: string;
    EditorMode = EditorMode;
    AssessmentType = AssessmentType;
    rerenderSubject = new Subject<void>();
    // This is used to revert the select if the user cancels to override the new selected programming language.
    private selectedProgrammingLanguageValue: ProgrammingLanguage;
    // This is used to revert the select if the user cancels to override the new selected project type.
    private selectedProjectTypeValue: ProjectType;

    maxPenaltyPattern = '^([0-9]|([1-9][0-9])|100)$';
    // Java package name Regex according to Java 14 JLS (https://docs.oracle.com/javase/specs/jls/se14/html/jls-7.html#jls-7.4.1),
    // with the restriction to a-z,A-Z,_ as "Java letter" and 0-9 as digits due to JavaScript/Browser Unicode character class limitations
    packageNamePatternForJavaKotlin =
        '^(?!.*(?:\\.|^)(?:abstract|continue|for|new|switch|assert|default|if|package|synchronized|boolean|do|goto|private|this|break|double|implements|protected|throw|byte|else|import|public|throws|case|enum|instanceof|return|transient|catch|extends|int|short|try|char|final|interface|static|void|class|finally|long|strictfp|volatile|const|float|native|super|while|_|true|false|null)(?:\\.|$))[A-Z_a-z][0-9A-Z_a-z]*(?:\\.[A-Z_a-z][0-9A-Z_a-z]*)*$';
    // Swift package name Regex derived from (https://docs.swift.org/swift-book/ReferenceManual/LexicalStructure.html#ID412),
    // with the restriction to a-z,A-Z as "Swift letter" and 0-9 as digits where no separators are allowed
    appNamePatternForSwift =
        '^(?!(?:associatedtype|class|deinit|enum|extension|fileprivate|func|import|init|inout|internal|let|open|operator|private|protocol|public|rethrows|static|struct|subscript|typealias|var|break|case|continue|default|defer|do|else|fallthrough|for|guard|if|in|repeat|return|switch|where|while|as|Any|catch|false|is|nil|super|self|Self|throw|throws|true|try|_|[sS]wift)$)[A-Za-z][0-9A-Za-z]*$';
    packageNamePattern = '';

    // Auxiliary Repository names must only include words or '-' characters.
    invalidRepositoryNamePattern = RegExp('^(?!(solution|exercise|tests|auxiliary)\\b)\\b(\\w|-)+$');

    // Auxiliary Repository checkout directories must be valid directory paths. Those must only include words,
    // '-' or '/' characters.
    invalidDirectoryNamePattern = RegExp('^[\\w-]+(/[\\w-]+)*$');

    // length of < 3 is also accepted in order to provide more accurate validation error messages
    readonly shortNamePattern = RegExp('(^(?![\\s\\S]))|^[a-zA-Z][a-zA-Z0-9]*$|' + shortNamePattern); // must start with a letter and cannot contain special characters
    titleNamePattern = '^[a-zA-Z0-9-_ ]+'; // must only contain alphanumeric characters, or whitespaces, or '_' or '-'

    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];

    public inProductionEnvironment: boolean;

    public supportsJava = true;
    public supportsPython = false;
    public supportsC = false;
    public supportsHaskell = false;
    public supportsKotlin = false;
    public supportsVHDL = false;
    public supportsAssembler = false;
    public supportsSwift = false;
    public supportsOCaml = false;
    public supportsEmpty = false;

    public packageNameRequired = true;
    public staticCodeAnalysisAllowed = false;
    public checkoutSolutionRepositoryAllowed = false;
    public sequentialTestRunsAllowed = false;
    public auxiliaryRepositoriesValid = true;

    // Additional options for import
    public recreateBuildPlans = false;
    public updateTemplate = false;
    public originalStaticCodeAnalysisEnabled: boolean | undefined;

    public projectTypes: ProjectType[] = [];
    // flag describing if the template and solution projects should include a dependency
    public withDependenciesValue = false;

    public modePickerOptions: ModePickerOption<ProjectType>[] = [];

    // Icons
    faSave = faSave;
    faBan = faBan;
    faQuestionCircle = faQuestionCircle;
    faExclamationCircle = faExclamationCircle;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private modalService: NgbModal,
        private popupService: ExerciseUpdateWarningService,
        private courseService: CourseManagementService,
        private alertService: AlertService,
        private exerciseService: ExerciseService,
        private fileService: FileService,
        private activatedRoute: ActivatedRoute,
        private translateService: TranslateService,
        private profileService: ProfileService,
        private exerciseGroupService: ExerciseGroupService,
        private programmingLanguageFeatureService: ProgrammingLanguageFeatureService,
        private navigationUtilService: ArtemisNavigationUtilService,
    ) {}

    /**
     * Updates the name of the editedAuxiliaryRepository.
     *
     * @param editedAuxiliaryRepository
     */
    updateRepositoryName(editedAuxiliaryRepository: AuxiliaryRepository) {
        return (newValue: any) => {
            editedAuxiliaryRepository.name = newValue;
            this.refreshAuxiliaryRepositoryChecks();
            return editedAuxiliaryRepository.name;
        };
    }

    /**
     * Updates the checkoutDirectory name of the editedAuxiliaryRepository.
     *
     * @param editedAuxiliaryRepository
     */
    updateCheckoutDirectory(editedAuxiliaryRepository: AuxiliaryRepository) {
        return (newValue: any) => {
            editedAuxiliaryRepository.checkoutDirectory = newValue;
            this.refreshAuxiliaryRepositoryChecks();
            return editedAuxiliaryRepository.checkoutDirectory;
        };
    }

    /**
     * Refreshes auxiliary variables for auxiliary repository checks. Those variables are
     * used in the template to display warnings.
     */
    refreshAuxiliaryRepositoryChecks() {
        let legalNameAndDirs = false;
        // Check that there are no duplicate names.
        const names = new Set<string | undefined>();
        const auxReposWithName = this.programmingExercise.auxiliaryRepositories!.filter((auxiliaryRepository) => auxiliaryRepository.name);
        auxReposWithName.forEach((auxiliaryRepository) => {
            names.add(auxiliaryRepository.name);
            legalNameAndDirs ||= !this.invalidRepositoryNamePattern.test(auxiliaryRepository.name!);
        });
        this.auxiliaryRepositoryDuplicateNames = names.size !== auxReposWithName.length;

        // Check that there are no duplicate checkout directories
        const directories = new Set<string | undefined>();
        const auxReposWithDirectory = this.programmingExercise.auxiliaryRepositories!.filter((auxiliaryRepository) => auxiliaryRepository.checkoutDirectory);
        auxReposWithDirectory.forEach((auxiliaryRepository) => {
            directories.add(auxiliaryRepository.checkoutDirectory);
            legalNameAndDirs ||= !this.invalidDirectoryNamePattern.test(auxiliaryRepository.checkoutDirectory!);
        });
        this.auxiliaryRepositoryDuplicateDirectories = directories.size !== auxReposWithDirectory.length;

        // Check that there are no empty/incorrect repository names and directories
        this.auxiliaryRepositoryNamedCorrectly = this.programmingExercise.auxiliaryRepositories!.length === auxReposWithName.length && !legalNameAndDirs;

        // Combining auxiliary variables to one to keep the template readable
        this.auxiliaryRepositoriesValid = this.auxiliaryRepositoryNamedCorrectly && !this.auxiliaryRepositoryDuplicateNames && !this.auxiliaryRepositoryDuplicateDirectories;
    }

    /**
     * Will also trigger loading the corresponding programming exercise language template.
     *
     * @param language to change to.
     */
    set selectedProgrammingLanguage(language: ProgrammingLanguage) {
        const languageChanged = this.selectedProgrammingLanguageValue !== language;
        this.selectedProgrammingLanguageValue = language;

        const programmingLanguageFeature = this.programmingLanguageFeatureService.getProgrammingLanguageFeature(language);
        this.packageNameRequired = programmingLanguageFeature.packageNameRequired;
        this.staticCodeAnalysisAllowed = programmingLanguageFeature.staticCodeAnalysis;
        this.checkoutSolutionRepositoryAllowed = programmingLanguageFeature.checkoutSolutionRepositoryAllowed;
        this.sequentialTestRunsAllowed = programmingLanguageFeature.sequentialTestRuns;
        // filter out MAVEN_MAVEN and GRADLE_GRADLE because they are not directly selectable but only via a checkbox
        this.projectTypes = programmingLanguageFeature.projectTypes.filter((projectType) => projectType !== ProjectType.MAVEN_MAVEN && projectType !== ProjectType.GRADLE_GRADLE);
        this.modePickerOptions = this.projectTypes.map((projectType) => ({
            value: projectType,
            labelKey: 'artemisApp.programmingExercise.projectTypes.' + projectType.toString(),
            btnClass: 'btn-secondary',
        }));

        if (languageChanged) {
            // Reset project type when changing programming language as not all programming languages support (the same) project types
            this.programmingExercise.projectType = this.projectTypes[0];
            this.selectedProjectTypeValue = this.projectTypes[0]!;
            this.withDependenciesValue = false;
        }

        // If we switch to another language which does not support static code analysis we need to reset options related to static code analysis
        if (!this.staticCodeAnalysisAllowed) {
            this.programmingExercise.staticCodeAnalysisEnabled = false;
            this.programmingExercise.maxStaticCodeAnalysisPenalty = undefined;
        }

        // Automatically enable the checkout of the solution repository for Haskell exercises
        this.programmingExercise.checkoutSolutionRepository = this.checkoutSolutionRepositoryAllowed && language === ProgrammingLanguage.HASKELL;

        // Only load problem statement template when creating a new exercise and not when importing an existing exercise
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
     * Will also trigger loading the corresponding project type template.
     *
     * @param type to change to.
     */
    set selectedProjectType(type: ProjectType) {
        // update the (selected) project type
        this.updateProjectTypeSettings(type);

        // Only load problem statement template when creating a new exercise and not when importing an existing exercise
        if (this.programmingExercise.id === undefined) {
            this.loadProgrammingLanguageTemplate(this.programmingExercise.programmingLanguage!);
            // Rerender the instructions as the template has changed.
            this.rerenderSubject.next();
        }
    }

    get selectedProjectType() {
        return this.selectedProjectTypeValue;
    }

    private updateProjectTypeSettings(type: ProjectType) {
        if (ProjectType.XCODE === type) {
            // Disable Online Editor
            this.programmingExercise.allowOnlineEditor = false;
        } else if (ProjectType.FACT === type) {
            // Disallow SCA for C (FACT)
            this.disableStaticCodeAnalysis();
        }

        // update the project types for java programming exercises according to whether dependencies should be included
        if (this.programmingExercise.programmingLanguage === ProgrammingLanguage.JAVA) {
            if (type === ProjectType.PLAIN_MAVEN || type === ProjectType.MAVEN_MAVEN) {
                this.selectedProjectTypeValue = ProjectType.PLAIN_MAVEN;
                if (this.withDependenciesValue) {
                    this.programmingExercise.projectType = ProjectType.MAVEN_MAVEN;
                } else {
                    this.programmingExercise.projectType = ProjectType.PLAIN_MAVEN;
                }
            } else {
                this.selectedProjectTypeValue = ProjectType.PLAIN_GRADLE;
                if (this.withDependenciesValue) {
                    this.programmingExercise.projectType = ProjectType.GRADLE_GRADLE;
                } else {
                    this.programmingExercise.projectType = ProjectType.PLAIN_GRADLE;
                }
            }
        } else {
            this.selectedProjectTypeValue = type;
            this.programmingExercise.projectType = type;
        }
    }

    /**
     * Only applies to Java programming exercises.
     * Will also trigger loading the corresponding project type template.
     * @param withDependencies whether the project should include a dependency
     */
    set withDependencies(withDependencies: boolean) {
        this.withDependenciesValue = withDependencies;
        this.selectedProjectType = this.programmingExercise.projectType!;
    }

    get withDependencies() {
        return this.withDependenciesValue;
    }

    private disableStaticCodeAnalysis() {
        this.programmingExercise.staticCodeAnalysisEnabled = false;
        this.programmingExercise.maxStaticCodeAnalysisPenalty = undefined;
    }

    /**
     * Sets the values for the creation/update of a programming exercise
     */
    ngOnInit() {
        this.isSaving = false;
        this.notificationText = undefined;
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
            this.backupExercise = cloneDeep(this.programmingExercise);
            this.selectedProgrammingLanguageValue = this.programmingExercise.programmingLanguage!;
            if (this.programmingExercise.projectType === ProjectType.MAVEN_MAVEN) {
                this.selectedProjectTypeValue = ProjectType.PLAIN_MAVEN;
            } else if (this.programmingExercise.projectType === ProjectType.GRADLE_GRADLE) {
                this.selectedProjectTypeValue = ProjectType.PLAIN_GRADLE;
            } else {
                this.selectedProjectTypeValue = this.programmingExercise.projectType!;
            }
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
                        if (params['courseId'] && params['examId'] && params['exerciseGroupId']) {
                            this.exerciseGroupService.find(params['courseId'], params['examId'], params['exerciseGroupId']).subscribe((res) => {
                                this.isExamMode = true;
                                this.programmingExercise.exerciseGroup = res.body!;
                            });
                        } else if (params['courseId']) {
                            const courseId = params['courseId'];
                            this.courseService.find(courseId).subscribe((res) => {
                                this.isExamMode = false;
                                this.programmingExercise.course = res.body!;
                                if (this.programmingExercise.course?.defaultProgrammingLanguage) {
                                    this.selectedProgrammingLanguage = this.programmingExercise.course.defaultProgrammingLanguage!;
                                }
                                this.exerciseCategories = this.programmingExercise.categories || [];
                                this.courseService.findAllCategoriesOfCourse(this.programmingExercise.course!.id!).subscribe({
                                    next: (categoryRes: HttpResponse<string[]>) => {
                                        this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body!);
                                    },
                                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                                });
                            });
                        }
                    }

                    // Set submit button text depending on component state
                    if (this.isImport) {
                        this.submitButtonTitle = 'entity.action.import';
                    } else if (this.programmingExercise.id) {
                        this.isEdit = true;
                        this.submitButtonTitle = 'entity.action.save';
                    } else {
                        this.submitButtonTitle = 'entity.action.generate';
                    }
                }),
            )
            .subscribe();
        // If an exercise is created, load our readme template so the problemStatement is not empty
        this.selectedProgrammingLanguage = this.programmingExercise.programmingLanguage!;
        if (this.programmingExercise.id) {
            this.problemStatementLoaded = true;
        }
        // Select the correct pattern
        this.setPackageNamePattern(this.selectedProgrammingLanguage);

        // Checks if the current environment is production
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.inProductionEnvironment = profileInfo.inProduction;
            }
        });

        this.supportsJava = this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.JAVA);
        this.supportsPython = this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.PYTHON);
        this.supportsC = this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.C);
        this.supportsHaskell = this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.HASKELL);
        this.supportsKotlin = this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.KOTLIN);
        this.supportsVHDL = this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.VHDL);
        this.supportsAssembler = this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.ASSEMBLER);
        this.supportsSwift = this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.SWIFT);
        this.supportsOCaml = this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.OCAML);
        this.supportsEmpty = this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.EMPTY);
    }

    /**
     * Setups the programming exercise for import. The route determine whether the new exercise will be imported as an exam
     * or a normal exercise.
     *
     * @param params given by ActivatedRoute
     */
    private createProgrammingExerciseForImport(params: Params) {
        this.isImport = true;
        this.originalStaticCodeAnalysisEnabled = this.programmingExercise.staticCodeAnalysisEnabled;
        // The source exercise is injected via the Resolver. The route parameters determine the target exerciseGroup or course
        if (params['courseId'] && params['examId'] && params['exerciseGroupId']) {
            this.exerciseGroupService.find(params['courseId'], params['examId'], params['exerciseGroupId']).subscribe((res) => {
                this.programmingExercise.exerciseGroup = res.body!;
                // Set course to undefined if a normal exercise is imported
                this.programmingExercise.course = undefined;
            });
            this.isExamMode = true;
        } else if (params['courseId']) {
            this.courseService.find(params['courseId']).subscribe((res) => {
                this.programmingExercise.course = res.body!;
                // Set exerciseGroup to undefined if an exam exercise is imported
                this.programmingExercise.exerciseGroup = undefined;
            });
            this.isExamMode = false;
        }
        resetDates(this.programmingExercise);

        this.programmingExercise.projectKey = undefined;
        this.programmingExercise.buildAndTestStudentSubmissionsAfterDueDate = undefined;
        this.programmingExercise.shortName = undefined;
        this.programmingExercise.title = undefined;
        if (this.programmingExercise.submissionPolicy) {
            this.programmingExercise.submissionPolicy.id = undefined;
        }
        if (this.isExamMode && this.programmingExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
            // Exam exercises cannot be not included into the total score. NOT_INCLUDED exercises will be converted to INCLUDED ones
            this.programmingExercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
        }
    }

    /**
     * Return to the previous page or a default if no previous page exists
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.programmingExercise);
    }

    /**
     * Updates the categories
     * @param categories which should be set
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.programmingExercise.categories = categories;
    }

    save() {
        if (this.programmingExercise.assessmentType === AssessmentType.SEMI_AUTOMATIC && this.programmingExercise.gradingInstructionFeedbackUsed) {
            const ref = this.popupService.checkExerciseBeforeUpdate(this.programmingExercise, this.backupExercise);
            if (!this.modalService.hasOpenModals()) {
                this.saveExercise();
            } else {
                ref.then((reference) => {
                    reference.componentInstance.confirmed.subscribe(() => {
                        this.saveExercise();
                    });
                    reference.componentInstance.reEvaluated.subscribe(() => {
                        const requestOptions = {} as any;
                        requestOptions.deleteFeedback = reference.componentInstance.deleteFeedback;
                        this.subscribeToSaveResponse(this.programmingExerciseService.reevaluateAndUpdate(this.programmingExercise, requestOptions));
                    });
                });
            }
        } else {
            this.saveExercise();
        }
    }

    /**
     * Saves the programming exercise with the provided input
     */
    saveExercise() {
        // If no release date is set, we warn the user.
        if (!this.programmingExercise.releaseDate && !this.isExamMode) {
            const confirmNoReleaseDate = this.translateService.instant(this.translationBasePath + 'noReleaseDateWarning');
            if (!window.confirm(confirmNoReleaseDate)) {
                return;
            }
        }

        // If the programming exercise has a submission policy with a NONE type, the policy is removed altogether
        if (this.programmingExercise.submissionPolicy && this.programmingExercise.submissionPolicy.type === SubmissionPolicyType.NONE) {
            this.programmingExercise.submissionPolicy = undefined;
        }

        Exercise.sanitize(this.programmingExercise);

        this.isSaving = true;

        if (this.exerciseService.hasExampleSolutionPublicationDateWarning(this.programmingExercise)) {
            this.alertService.addAlert({
                type: AlertType.WARNING,
                message: 'artemisApp.exercise.exampleSolutionPublicationDateWarning',
            });
        }

        if (this.isImport) {
            this.subscribeToSaveResponse(this.programmingExerciseService.importExercise(this.programmingExercise, this.recreateBuildPlans, this.updateTemplate));
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
        result.subscribe({
            next: () => this.onSaveSuccess(),
            error: (error: HttpErrorResponse) => this.onSaveError(error),
        });
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError(error: HttpErrorResponse) {
        const errorMessage = error.headers.get('X-artemisApp-alert')!;
        this.alertService.addAlert({
            type: AlertType.DANGER,
            message: errorMessage,
            disableTranslation: true,
        });
        this.isSaving = false;
        window.scrollTo(0, 0);
    }

    /**
     * When setting the programming language, a change guard is triggered.
     * This is because we want to reload the instructions template for a different language, but don't want the user to lose unsaved changes.
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
        // Select the correct pattern
        this.setPackageNamePattern(language);
        this.selectedProgrammingLanguage = language;
        return language;
    }

    /**
     * Sets the regex pattern for the package name for the selected programming language.
     *
     * @param language to choose from
     */
    setPackageNamePattern(language: ProgrammingLanguage) {
        if (language === ProgrammingLanguage.SWIFT) {
            this.packageNamePattern = this.appNamePatternForSwift;
        } else {
            this.packageNamePattern = this.packageNamePatternForJavaKotlin;
        }
    }

    /**
     * When setting the project type, a change guard is triggered.
     * This is because we want to reload the instructions template for a project type, but don't want the user to lose unsaved changes.
     * If the user cancels the project type will not be changed.
     *
     * @param projectType to switch to.
     */
    onProjectTypeChange(projectType: ProjectType) {
        // If there are unsaved changes and the user does not confirm, the language doesn't get changed
        if (this.hasUnsavedChanges) {
            const confirmLanguageChangeText = this.translateService.instant(this.translationBasePath + 'unsavedChangesProjectTypeChange');
            if (!window.confirm(confirmLanguageChangeText)) {
                return this.selectedProjectType;
            }
        }
        this.selectedProjectType = projectType;
        return projectType;
    }

    onStaticCodeAnalysisChanged() {
        // On import: If SCA mode changed, activate recreation of build plans and update of the template
        if (this.isImport && this.programmingExercise.staticCodeAnalysisEnabled !== this.originalStaticCodeAnalysisEnabled) {
            this.recreateBuildPlans = true;
            this.updateTemplate = true;
        }

        if (!this.programmingExercise.staticCodeAnalysisEnabled) {
            this.programmingExercise.maxStaticCodeAnalysisPenalty = undefined;
        }
    }

    onRecreateBuildPlanOrUpdateTemplateChange() {
        if (!this.recreateBuildPlans || !this.updateTemplate) {
            this.programmingExercise.staticCodeAnalysisEnabled = this.originalStaticCodeAnalysisEnabled;
        }

        if (!this.programmingExercise.staticCodeAnalysisEnabled) {
            this.programmingExercise.maxStaticCodeAnalysisPenalty = undefined;
        }
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
        this.fileService.getTemplateFile('readme', this.programmingExercise.programmingLanguage, this.programmingExercise.projectType).subscribe({
            next: (file) => {
                this.programmingExercise.problemStatement = file;
                this.problemStatementLoaded = true;
            },
            error: () => {
                this.programmingExercise.problemStatement = '';
                this.problemStatementLoaded = true;
            },
        });
    }

    /**
     * checking if at least one of Online Editor or Offline Ide is selected
     */
    validIdeSelection() {
        return this.programmingExercise.allowOnlineEditor || this.programmingExercise.allowOfflineIde;
    }

    isEventInsideTextArea(event: Event): boolean {
        if (event.target instanceof Element) {
            return event.target.tagName === 'TEXTAREA';
        }
        return false;
    }

    /**
     * Get a list of all reasons that describe why the current input to update is invalid
     */
    getInvalidReasons(): ValidationReason[] {
        const validationErrorReasons: ValidationReason[] = [];

        this.validateExerciseTitle(validationErrorReasons);
        this.validateExerciseShortName(validationErrorReasons);
        this.validateExercisePoints(validationErrorReasons);
        this.validateExerciseBonusPoints(validationErrorReasons);
        this.validateExerciseSCAMaxPenalty(validationErrorReasons);
        this.validateExercisePackageName(validationErrorReasons);
        this.validateExerciseSubmissionLimit(validationErrorReasons);
        this.validateExerciseAuxiliryRepositories(validationErrorReasons);
        this.validateExerciseIdeSelection(validationErrorReasons);

        return validationErrorReasons;
    }

    private validateExerciseTitle(validationErrorReasons: ValidationReason[]): void {
        if (this.programmingExercise.title === undefined || this.programmingExercise.title === '') {
            validationErrorReasons.push({
                translateKey: 'artemisApp.exercise.form.title.undefined',
                translateValues: {},
            });
        } else if (this.programmingExercise.title.match(this.titleNamePattern) === null || this.programmingExercise.title?.match(this.titleNamePattern)?.length === 0) {
            validationErrorReasons.push({
                translateKey: 'artemisApp.exercise.form.title.pattern',
                translateValues: {},
            });
        }
    }

    private validateExerciseShortName(validationErrorReasons: ValidationReason[]): void {
        if (this.programmingExercise.shortName === undefined || this.programmingExercise.shortName === '') {
            validationErrorReasons.push({
                translateKey: 'artemisApp.exercise.form.shortName.undefined',
                translateValues: {},
            });
        } else {
            if (this.programmingExercise.shortName.length < 3) {
                validationErrorReasons.push({
                    translateKey: 'artemisApp.exercise.form.shortName.minlength',
                    translateValues: {},
                });
            }
            const shortNamePatternMatch = this.programmingExercise.shortName.match(this.shortNamePattern);
            if (shortNamePatternMatch === null || shortNamePatternMatch.length === 0) {
                validationErrorReasons.push({
                    translateKey: 'artemisApp.exercise.form.shortName.pattern',
                    translateValues: {},
                });
            }
        }
    }

    private validateExercisePoints(validationErrorReasons: ValidationReason[]): void {
        if (this.programmingExercise.maxPoints === undefined) {
            validationErrorReasons.push({
                translateKey: 'artemisApp.exercise.form.points.undefined',
                translateValues: {},
            });
        } else if (this.programmingExercise.maxPoints < 1) {
            validationErrorReasons.push({
                translateKey: 'artemisApp.exercise.form.points.customMin',
                translateValues: {},
            });
        } else if (this.programmingExercise.maxPoints > 9999) {
            validationErrorReasons.push({
                translateKey: 'artemisApp.exercise.form.points.customMax',
                translateValues: {},
            });
        }
    }

    private validateExerciseBonusPoints(validationErrorReasons: ValidationReason[]) {
        if (this.programmingExercise.bonusPoints === undefined || typeof this.programmingExercise.bonusPoints !== 'number') {
            validationErrorReasons.push({
                translateKey: 'artemisApp.exercise.form.bonusPoints.undefined',
                translateValues: {},
            });
        } else if (this.programmingExercise.bonusPoints! < 0) {
            validationErrorReasons.push({
                translateKey: 'artemisApp.exercise.form.bonusPoints.customMin',
                translateValues: {},
            });
        } else if (this.programmingExercise.bonusPoints! > 9999) {
            validationErrorReasons.push({
                translateKey: 'artemisApp.exercise.form.bonusPoints.customMax',
                translateValues: {},
            });
        }
    }

    private validateExerciseSCAMaxPenalty(validationErrorReasons: ValidationReason[]) {
        const maxStaticCodeAnalysisPenaltyPatternMatch = this.programmingExercise.maxStaticCodeAnalysisPenalty?.toString().match(this.maxPenaltyPattern);
        if (maxStaticCodeAnalysisPenaltyPatternMatch === null || maxStaticCodeAnalysisPenaltyPatternMatch?.length === 0) {
            validationErrorReasons.push({
                translateKey: 'artemisApp.exercise.form.maxPenalty.pattern',
                translateValues: {},
            });
        }
    }

    private validateExercisePackageName(validationErrorReasons: ValidationReason[]): void {
        // validation on package name has only to be performed for Java, Kotlin and Swift
        if (
            this.programmingExercise.programmingLanguage !== ProgrammingLanguage.JAVA &&
            this.programmingExercise.programmingLanguage !== ProgrammingLanguage.KOTLIN &&
            this.programmingExercise.programmingLanguage !== ProgrammingLanguage.SWIFT
        ) {
            return;
        }

        if (this.programmingExercise.packageName === undefined || this.programmingExercise.packageName === '') {
            validationErrorReasons.push({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        } else {
            const patternMatchJavaKotlin: RegExpMatchArray | null = this.programmingExercise.packageName.match(this.packageNamePatternForJavaKotlin);
            const patternMatchSwift: RegExpMatchArray | null = this.programmingExercise.packageName.match(this.appNamePatternForSwift);
            if (this.programmingExercise.programmingLanguage === ProgrammingLanguage.JAVA && (patternMatchJavaKotlin === null || patternMatchJavaKotlin.length === 0)) {
                validationErrorReasons.push({
                    translateKey: 'artemisApp.exercise.form.packageName.pattern.JAVA',
                    translateValues: {},
                });
            } else if (this.programmingExercise.programmingLanguage === ProgrammingLanguage.KOTLIN && (patternMatchJavaKotlin === null || patternMatchJavaKotlin.length === 0)) {
                validationErrorReasons.push({
                    translateKey: 'artemisApp.exercise.form.packageName.pattern.KOTLIN',
                    translateValues: {},
                });
            } else if (this.programmingExercise.programmingLanguage === ProgrammingLanguage.SWIFT && (patternMatchSwift === null || patternMatchSwift.length === 0)) {
                validationErrorReasons.push({
                    translateKey: 'artemisApp.exercise.form.packageName.pattern.SWIFT',
                    translateValues: {},
                });
            }
        }
    }

    private validateExerciseSubmissionLimit(validationErrorReasons: ValidationReason[]): void {
        // verifying submission limit value
        if (this.programmingExercise.submissionPolicy !== undefined && this.programmingExercise.submissionPolicy.type !== SubmissionPolicyType.NONE) {
            const submissionLimit = this.programmingExercise.submissionPolicy?.submissionLimit;
            if (submissionLimit === undefined || typeof submissionLimit !== 'number') {
                validationErrorReasons.push({
                    translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitWarning.required',
                    translateValues: {},
                });
            } else if (submissionLimit < 1 || submissionLimit > 500 || submissionLimit % 1 !== 0) {
                validationErrorReasons.push({
                    translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitWarning.pattern',
                    translateValues: {},
                });
            }
        }

        // verifying exceeding submission limit penalty
        if (this.programmingExercise.submissionPolicy?.type === SubmissionPolicyType.SUBMISSION_PENALTY) {
            const exceedingPenalty = this.programmingExercise.submissionPolicy?.exceedingPenalty;
            if (exceedingPenalty === undefined || typeof exceedingPenalty !== 'number') {
                validationErrorReasons.push({
                    translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInputFieldValidationWarning.required',
                    translateValues: {},
                });
            } else if (exceedingPenalty <= 0) {
                validationErrorReasons.push({
                    translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInputFieldValidationWarning.pattern',
                    translateValues: {},
                });
            }
        }
    }

    private validateExerciseAuxiliryRepositories(validationErrorReasons: ValidationReason[]): void {
        if (!this.auxiliaryRepositoriesValid) {
            validationErrorReasons.push({
                translateKey: 'artemisApp.programmingExercise.auxiliaryRepository.error',
                translateValues: {},
            });
        }
    }

    private validateExerciseIdeSelection(validationErrorReasons: ValidationReason[]): void {
        if (!this.validIdeSelection()) {
            validationErrorReasons.push({
                translateKey: 'artemisApp.programmingExercise.allowOnlineEditor.alert',
                translateValues: {},
            });
        }
    }
}
