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
import { Exercise, IncludedInOverallScore, ValidationReason, resetDates } from 'app/entities/exercise.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ProgrammingLanguageFeatureService } from 'app/exercises/programming/shared/service/programming-language-feature/programming-language-feature.service';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { SHORT_NAME_PATTERN } from 'app/shared/constants/input.constants';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { cloneDeep } from 'lodash-es';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { onError } from 'app/shared/util/global.utils';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { faBan, faExclamationCircle, faHandshakeAngle, faQuestionCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { ModePickerOption } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';

@Component({
    selector: 'jhi-programming-exercise-update',
    templateUrl: './programming-exercise-update.component.html',
    styleUrls: ['../programming-exercise-form.scss'],
})
export class ProgrammingExerciseUpdateComponent implements OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly FeatureToggle = FeatureToggle;
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ProjectType = ProjectType;

    private translationBasePath = 'artemisApp.programmingExercise.';

    toggleMode = () => this.toggleWizardMode();
    getInvalidReasonsForWizard = () => this.getInvalidReasons(this.currentWizardModeStep);
    programmingLanguageChanged = (language: ProgrammingLanguage) => this.onProgrammingLanguageChange(language);
    withDependenciesChanged = (withDependencies: boolean) => this.onWithDependenciesChanged(withDependencies);
    categoriesChanged = (categories: ExerciseCategory[]) => this.updateCategories(categories);
    projectTypeChanged = (projectType: ProjectType) => this.onProjectTypeChange(projectType);
    staticCodeAnalysisChanged = () => this.onStaticCodeAnalysisChanged();
    currentWizardModeStep = 1;

    auxiliaryRepositoryDuplicateNames: boolean;
    auxiliaryRepositoryDuplicateDirectories: boolean;
    auxiliaryRepositoryNamedCorrectly: boolean;
    submitButtonTitle: string;
    isImportFromExistingExercise: boolean;
    isImportFromFile: boolean;
    isEdit: boolean;
    isExamMode: boolean;
    isShowingWizardMode = false;
    hasUnsavedChanges = false;
    programmingExercise: ProgrammingExercise;
    backupExercise: ProgrammingExercise;
    isSaving: boolean;
    goBackAfterSaving = false;
    problemStatementLoaded = false;
    templateParticipationResultLoaded = true;
    notificationText?: string;
    courseId: number;

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
    // No dots allowed for the blackbox project type, because the folder naming works slightly different here.
    packageNamePatternForJavaBlackbox =
        '^(?!.*(?:\\.|^)(?:abstract|continue|for|new|switch|assert|default|if|package|synchronized|boolean|do|goto|private|this|break|double|implements|protected|throw|byte|else|import|public|throws|case|enum|instanceof|return|transient|catch|extends|int|short|try|char|final|interface|static|void|class|finally|long|strictfp|volatile|const|float|native|super|while|_|true|false|null)(?:\\.|$))[A-Z_a-z][0-9A-Z_a-z]*$';
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
    readonly shortNamePattern = RegExp('(^(?![\\s\\S]))|^[a-zA-Z][a-zA-Z0-9]*$|' + SHORT_NAME_PATTERN); // must start with a letter and cannot contain special characters
    titleNamePattern = '^[a-zA-Z0-9-_ ]+'; // must only contain alphanumeric characters, or whitespaces, or '_' or '-'

    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];

    public inProductionEnvironment: boolean;

    public supportedLanguages = ['java'];

    public packageNameRequired = true;
    public staticCodeAnalysisAllowed = false;
    public checkoutSolutionRepositoryAllowed = false;
    public sequentialTestRunsAllowed = false;
    public publishBuildPlanUrlAllowed = false;
    public testwiseCoverageAnalysisSupported = false;
    public auxiliaryRepositoriesSupported = false;
    public auxiliaryRepositoriesValid = true;

    // Additional options for import
    public recreateBuildPlans = false;
    public updateTemplate = false;
    public originalStaticCodeAnalysisEnabled: boolean | undefined;

    public projectTypes: ProjectType[] = [];
    // flag describing if the template and solution projects should include a dependency
    public withDependenciesValue = false;

    public modePickerOptions: ModePickerOption<ProjectType>[] = [];

    documentationType = DocumentationType.Programming;

    // Icons
    faSave = faSave;
    faBan = faBan;
    faHandShakeAngle = faHandshakeAngle;
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
     * Activate or deactivate the wizard mode for easier exercise creation.
     * This function is called by pressing "Switch to guided mode" when creating a new exercise
     */
    toggleWizardMode() {
        this.isShowingWizardMode = !this.isShowingWizardMode;
    }

    /**
     * Progress to the next step of the wizard mode
     */
    nextWizardStep() {
        this.currentWizardModeStep++;

        if (this.currentWizardModeStep > 5) {
            this.save();
        }
    }

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
        this.publishBuildPlanUrlAllowed = programmingLanguageFeature.publishBuildPlanUrlAllowed;
        this.testwiseCoverageAnalysisSupported = programmingLanguageFeature.testwiseCoverageAnalysisSupported;
        this.auxiliaryRepositoriesSupported = programmingLanguageFeature.auxiliaryRepositoriesSupported;
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
        if (this.programmingExercise.id === undefined && !this.isImportFromFile) {
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
        if (this.programmingExercise.id === undefined && !this.isImportFromFile) {
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
            if (type == ProjectType.MAVEN_BLACKBOX) {
                this.selectedProjectTypeValue = ProjectType.MAVEN_BLACKBOX;
                this.programmingExercise.projectType = ProjectType.MAVEN_BLACKBOX;
                this.sequentialTestRunsAllowed = false;
                this.testwiseCoverageAnalysisSupported = false;
            } else if (type === ProjectType.PLAIN_MAVEN || type === ProjectType.MAVEN_MAVEN) {
                this.selectedProjectTypeValue = ProjectType.PLAIN_MAVEN;
                this.sequentialTestRunsAllowed = true;
                this.testwiseCoverageAnalysisSupported = true;
                if (this.withDependenciesValue) {
                    this.programmingExercise.projectType = ProjectType.MAVEN_MAVEN;
                } else {
                    this.programmingExercise.projectType = ProjectType.PLAIN_MAVEN;
                }
            } else {
                this.selectedProjectTypeValue = ProjectType.PLAIN_GRADLE;
                this.sequentialTestRunsAllowed = true;
                this.testwiseCoverageAnalysisSupported = true;
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

        // If it is an import from this instance, just get the course, otherwise handle the edit and new cases
        this.activatedRoute.url
            .pipe(
                tap((segments) => {
                    this.isImportFromExistingExercise = segments.some((segment) => segment.path === 'import');
                    this.isImportFromFile = segments.some((segment) => segment.path === 'import-from-file');
                }),
                switchMap(() => this.activatedRoute.params),
                tap((params) => {
                    if (this.isImportFromFile) {
                        this.createProgrammingExerciseForImportFromFile();
                    }
                    if (this.isImportFromExistingExercise) {
                        this.createProgrammingExerciseForImport(params);
                    } else {
                        if (params['courseId'] && params['examId'] && params['exerciseGroupId']) {
                            this.isExamMode = true;
                            this.exerciseGroupService.find(params['courseId'], params['examId'], params['exerciseGroupId']).subscribe((res) => {
                                this.programmingExercise.exerciseGroup = res.body!;
                            });
                            // we need the course id  to make the request to the server if it's an import from file
                            if (this.isImportFromFile) {
                                this.courseId = params['courseId'];
                            }
                        } else if (params['courseId']) {
                            this.courseId = params['courseId'];
                            this.isExamMode = false;
                            this.courseService.find(this.courseId).subscribe((res) => {
                                this.programmingExercise.course = res.body!;
                                if (this.programmingExercise.course?.defaultProgrammingLanguage && !this.isImportFromFile) {
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
                    if (this.isImportFromExistingExercise || this.isImportFromFile) {
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

        this.activatedRoute.queryParams.subscribe((params) => {
            if (params.shouldHaveBackButtonToWizard) {
                this.goBackAfterSaving = true;
            }
        });

        // If an exercise is created, load our readme template so the problemStatement is not empty
        this.selectedProgrammingLanguage = this.programmingExercise.programmingLanguage!;
        if (this.programmingExercise.id || this.isImportFromFile) {
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

        this.supportedLanguages = [];

        if (this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.JAVA)) {
            this.supportedLanguages.push(ProgrammingLanguage.JAVA);
        }
        if (this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.PYTHON)) {
            this.supportedLanguages.push(ProgrammingLanguage.PYTHON);
        }
        if (this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.C)) {
            this.supportedLanguages.push(ProgrammingLanguage.C);
        }
        if (this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.HASKELL)) {
            this.supportedLanguages.push(ProgrammingLanguage.HASKELL);
        }
        if (this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.KOTLIN)) {
            this.supportedLanguages.push(ProgrammingLanguage.KOTLIN);
        }
        if (this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.VHDL)) {
            this.supportedLanguages.push(ProgrammingLanguage.VHDL);
        }
        if (this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.ASSEMBLER)) {
            this.supportedLanguages.push(ProgrammingLanguage.ASSEMBLER);
        }
        if (this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.SWIFT)) {
            this.supportedLanguages.push(ProgrammingLanguage.SWIFT);
        }
        if (this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.OCAML)) {
            this.supportedLanguages.push(ProgrammingLanguage.OCAML);
        }
        if (this.programmingLanguageFeatureService.supportsProgrammingLanguage(ProgrammingLanguage.EMPTY)) {
            this.supportedLanguages.push(ProgrammingLanguage.EMPTY);
        }
    }

    /**
     * Setups the programming exercise for import. The route determine whether the new exercise will be imported as an exam
     * or a normal exercise.
     *
     * @param params given by ActivatedRoute
     */
    private createProgrammingExerciseForImport(params: Params) {
        this.isImportFromExistingExercise = true;
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
     * Return to the exercise overview page
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
        this.exerciseCategories = categories;
    }

    save() {
        const ref = this.popupService.checkExerciseBeforeUpdate(this.programmingExercise, this.backupExercise, this.isExamMode);
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
    }

    /**
     * Saves the programming exercise with the provided input
     */
    saveExercise() {
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

        // If the auxiliary repositories were edited after the creation of the exercise, the changes have to be done manually in the VCS and CIS
        const changedAuxiliaryRepository =
            (this.programmingExercise.auxiliaryRepositories?.length ?? 0) < (this.backupExercise?.auxiliaryRepositories?.length ?? 0) ||
            this.programmingExercise.auxiliaryRepositories?.some((auxRepo, index) => {
                const otherAuxRepo = this.backupExercise?.auxiliaryRepositories?.[index];
                return !otherAuxRepo || auxRepo.name !== otherAuxRepo.name || auxRepo.checkoutDirectory !== otherAuxRepo.checkoutDirectory;
            });
        if (changedAuxiliaryRepository && this.programmingExercise.id) {
            this.alertService.addAlert({
                type: AlertType.WARNING,
                message: 'artemisApp.programmingExercise.auxiliaryRepository.editedWarning',
            });
        }
        if (this.isImportFromFile) {
            this.subscribeToSaveResponse(this.programmingExerciseService.importFromFile(this.programmingExercise, this.courseId));
        } else if (this.isImportFromExistingExercise) {
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
            next: (response: HttpResponse<ProgrammingExercise>) => this.onSaveSuccess(response.body!),
            error: (error: HttpErrorResponse) => this.onSaveError(error),
        });
    }

    private onSaveSuccess(exercise: ProgrammingExercise) {
        this.isSaving = false;

        if (this.goBackAfterSaving) {
            this.navigationUtilService.navigateBack();

            return;
        }

        this.navigationUtilService.navigateForwardFromExerciseUpdateOrCreation(exercise);
    }

    private onSaveError(error: HttpErrorResponse) {
        let errorMessage;
        let disableTranslation;
        // Workaround for conflict error, since conflict errors do not have the 'X-artemisApp-alert' header
        if (error.status === 409 && error.error && error.error['X-artemisApp-error'] === 'error.sourceExerciseInconsistent') {
            errorMessage = 'artemisApp.consistencyCheck.error.programmingExerciseImportFailed';
            disableTranslation = false;
        } else {
            errorMessage = error.headers.get('X-artemisApp-alert')!;
            disableTranslation = true;
        }
        this.alertService.addAlert({
            type: AlertType.DANGER,
            message: errorMessage,
            disableTranslation: disableTranslation,
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
     * @param useBlackboxPattern whether to allow points in the regex
     */
    setPackageNamePattern(language: ProgrammingLanguage, useBlackboxPattern = false) {
        if (language === ProgrammingLanguage.SWIFT) {
            this.packageNamePattern = this.appNamePatternForSwift;
        } else {
            this.packageNamePattern = useBlackboxPattern ? this.packageNamePatternForJavaBlackbox : this.packageNamePatternForJavaKotlin;
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
        const useBlackboxPattern = projectType === ProjectType.MAVEN_BLACKBOX;
        this.setPackageNamePattern(this.programmingExercise.programmingLanguage!, useBlackboxPattern);
        return projectType;
    }

    onWithDependenciesChanged(withDependencies: boolean) {
        this.withDependenciesValue = withDependencies;

        return withDependencies;
    }

    onStaticCodeAnalysisChanged() {
        // On import: If SCA mode changed, activate recreation of build plans and update of the template
        if (this.isImportFromExistingExercise && this.programmingExercise.staticCodeAnalysisEnabled !== this.originalStaticCodeAnalysisEnabled) {
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
        this.fileService.getTemplateFile(this.programmingExercise.programmingLanguage, this.programmingExercise.projectType).subscribe({
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
    validIdeSelection = () => {
        return this.programmingExercise?.allowOnlineEditor || this.programmingExercise?.allowOfflineIde;
    };

    isEventInsideTextArea(event: Event): boolean {
        if (event.target instanceof Element) {
            return event.target.tagName === 'TEXTAREA';
        }
        return false;
    }

    /**
     * Get a list of all reasons that describe why the current input to update is invalid
     *
     * @param forStep Limit the respected invalid reasons to the current wizard mode step. By default, e.g. when not using the wizard, all reasons are respected.
     */
    getInvalidReasons(forStep = Number.MAX_VALUE): ValidationReason[] {
        const validationErrorReasons: ValidationReason[] = [];

        if (forStep >= 1) {
            this.validateExerciseTitle(validationErrorReasons);
            this.validateExerciseChannelName(validationErrorReasons);
            this.validateExerciseShortName(validationErrorReasons);
            this.validateExerciseAuxiliryRepositories(validationErrorReasons);
        }

        if (forStep >= 3) {
            this.validateExercisePackageName(validationErrorReasons);
        }

        if (forStep >= 4) {
            this.validateExerciseIdeSelection(validationErrorReasons);
        }

        if (forStep >= 5) {
            this.validateExercisePoints(validationErrorReasons);
            this.validateExerciseBonusPoints(validationErrorReasons);
            this.validateExerciseSCAMaxPenalty(validationErrorReasons);
            this.validateExerciseSubmissionLimit(validationErrorReasons);
        }

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

    private validateExerciseChannelName(validationErrorReasons: ValidationReason[]): void {
        if (this.programmingExercise.channelName === '') {
            validationErrorReasons.push({
                translateKey: 'artemisApp.exercise.form.channelName.empty',
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
            const patternMatchJavaBlackbox: RegExpMatchArray | null = this.programmingExercise.packageName.match(this.packageNamePatternForJavaBlackbox);
            const patternMatchSwift: RegExpMatchArray | null = this.programmingExercise.packageName.match(this.appNamePatternForSwift);
            const projectTypeDependentPatternMatch: RegExpMatchArray | null =
                this.programmingExercise.projectType === ProjectType.MAVEN_BLACKBOX ? patternMatchJavaBlackbox : patternMatchJavaKotlin;

            if (
                this.programmingExercise.programmingLanguage === ProgrammingLanguage.JAVA &&
                (projectTypeDependentPatternMatch === null || projectTypeDependentPatternMatch.length === 0)
            ) {
                if (this.programmingExercise.projectType === ProjectType.MAVEN_BLACKBOX) {
                    validationErrorReasons.push({
                        translateKey: 'artemisApp.exercise.form.packageName.pattern.JAVA_BLACKBOX',
                        translateValues: {},
                    });
                } else {
                    validationErrorReasons.push({
                        translateKey: 'artemisApp.exercise.form.packageName.pattern.JAVA',
                        translateValues: {},
                    });
                }
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

    private createProgrammingExerciseForImportFromFile() {
        this.programmingExercise = cloneDeep(history.state.programmingExerciseForImportFromFile);
        this.programmingExercise.id = undefined;
        this.programmingExercise.exerciseGroup = undefined;
        this.programmingExercise.course = undefined;
        this.programmingExercise.projectKey = undefined;
        this.programmingExercise.dueDate = undefined;
        this.programmingExercise.assessmentDueDate = undefined;
        this.programmingExercise.releaseDate = undefined;
        this.programmingExercise.startDate = undefined;
        this.programmingExercise.exampleSolutionPublicationDate = undefined;
        //without dates set, they can only be false
        this.programmingExercise.allowComplaintsForAutomaticAssessments = false;
        this.programmingExercise.allowManualFeedbackRequests = false;
        this.selectedProgrammingLanguage = this.programmingExercise.programmingLanguage!;
        // we need to get it from the history object as setting the programming language
        // sets the project type of the programming exercise to the default value for the programming language.
        this.selectedProjectType = history.state.programmingExerciseForImportFromFile.projectType;
    }

    getProgrammingExerciseCreationConfig(): ProgrammingExerciseCreationConfig {
        return {
            isImportFromFile: this.isImportFromFile,
            isImportFromExistingExercise: this.isImportFromExistingExercise,
            showSummary: false,
            isEdit: this.isEdit,
            isExamMode: this.isExamMode,
            auxiliaryRepositoriesSupported: this.auxiliaryRepositoriesSupported,
            auxiliaryRepositoryDuplicateDirectories: this.auxiliaryRepositoryDuplicateDirectories,
            auxiliaryRepositoryDuplicateNames: this.auxiliaryRepositoryDuplicateNames,
            checkoutSolutionRepositoryAllowed: this.checkoutSolutionRepositoryAllowed,
            invalidDirectoryNamePattern: this.invalidDirectoryNamePattern,
            invalidRepositoryNamePattern: this.invalidRepositoryNamePattern,
            titleNamePattern: this.titleNamePattern,
            shortNamePattern: this.shortNamePattern,
            updateRepositoryName: this.updateRepositoryName,
            updateCheckoutDirectory: this.updateCheckoutDirectory,
            refreshAuxiliaryRepositoryChecks: this.refreshAuxiliaryRepositoryChecks,
            exerciseCategories: this.exerciseCategories,
            existingCategories: this.existingCategories,
            updateCategories: this.categoriesChanged,
            appNamePatternForSwift: this.appNamePatternForSwift,
            modePickerOptions: this.modePickerOptions,
            withDependencies: this.withDependencies,
            onWithDependenciesChanged: this.withDependenciesChanged,
            packageNameRequired: this.packageNameRequired,
            packageNamePattern: this.packageNamePattern,
            supportedLanguages: this.supportedLanguages,
            selectedProgrammingLanguage: this.selectedProgrammingLanguage,
            onProgrammingLanguageChange: this.programmingLanguageChanged,
            projectTypes: this.projectTypes,
            selectedProjectType: this.selectedProjectType,
            onProjectTypeChange: this.projectTypeChanged,
            sequentialTestRunsAllowed: this.sequentialTestRunsAllowed,
            testwiseCoverageAnalysisSupported: this.testwiseCoverageAnalysisSupported,
            staticCodeAnalysisAllowed: this.staticCodeAnalysisAllowed,
            onStaticCodeAnalysisChanged: this.staticCodeAnalysisChanged,
            maxPenaltyPattern: this.maxPenaltyPattern,
            problemStatementLoaded: this.problemStatementLoaded,
            templateParticipationResultLoaded: this.templateParticipationResultLoaded,
            hasUnsavedChanges: this.hasUnsavedChanges,
            rerenderSubject: this.rerenderSubject.asObservable(),
            validIdeSelection: this.validIdeSelection,
            inProductionEnvironment: this.inProductionEnvironment,
            recreateBuildPlans: this.recreateBuildPlans,
            onRecreateBuildPlanOrUpdateTemplateChange: this.onRecreateBuildPlanOrUpdateTemplateChange,
            updateTemplate: this.updateTemplate,
            publishBuildPlanUrlAllowed: this.publishBuildPlanUrlAllowed,
            recreateBuildPlanOrUpdateTemplateChange: this.onRecreateBuildPlanOrUpdateTemplateChange,
        };
    }
}
