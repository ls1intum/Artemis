import { ActivatedRoute, Params } from '@angular/router';
import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild, computed, effect, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { ProgrammingExerciseBuildConfig } from 'app/entities/programming/programming-exercise-build.config';
import { Observable, Subject, Subscription } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType, resetProgrammingForImport } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseService } from '../services/programming-exercise.service';
import { FileService } from 'app/shared/http/file.service';
import { TranslateService } from '@ngx-translate/core';
import { switchMap, tap } from 'rxjs/operators';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Exercise, IncludedInOverallScore, ValidationReason } from 'app/entities/exercise.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ProgrammingLanguageFeatureService } from 'app/exercises/programming/shared/service/programming-language-feature/programming-language-feature.service';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { EXERCISE_TITLE_NAME_PATTERN, EXERCISE_TITLE_NAME_REGEX, SHORT_NAME_PATTERN } from 'app/shared/constants/input.constants';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { cloneDeep } from 'lodash-es';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AuxiliaryRepository } from 'app/entities/programming/programming-exercise-auxiliary-repository-model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { faExclamationCircle, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ModePickerOption } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { loadCourseExerciseCategories } from 'app/exercises/shared/course-exercises/course-utils';
import { PROFILE_AEOLUS, PROFILE_LOCALCI, PROFILE_THEIA } from 'app/app.constants';
import { AeolusService } from 'app/exercises/programming/shared/service/aeolus.service';
import { FormSectionStatus } from 'app/forms/form-status-bar/form-status-bar.component';
import { ProgrammingExerciseInformationComponent } from 'app/exercises/programming/manage/update/update-components/information/programming-exercise-information.component';
import { ProgrammingExerciseModeComponent } from 'app/exercises/programming/manage/update/update-components/mode/programming-exercise-mode.component';
import { ProgrammingExerciseLanguageComponent } from 'app/exercises/programming/manage/update/update-components/language/programming-exercise-language.component';
import { ProgrammingExerciseGradingComponent } from 'app/exercises/programming/manage/update/update-components/grading/programming-exercise-grading.component';
import { ExerciseUpdatePlagiarismComponent } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { ImportOptions } from 'app/types/programming-exercises';
import { INPUT_FIELD_EDIT_MODE_MAPPING, ProgrammingExerciseInputField } from 'app/exercises/programming/manage/update/programming-exercise-update.helper';

@Component({
    selector: 'jhi-programming-exercise-update',
    templateUrl: './programming-exercise-update.component.html',
    styleUrls: ['../programming-exercise-form.scss'],
})
export class ProgrammingExerciseUpdateComponent implements AfterViewInit, OnDestroy, OnInit {
    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly ProjectType = ProjectType;
    protected readonly documentationType: DocumentationType = 'Programming';
    protected readonly AssessmentType = AssessmentType;
    protected readonly maxPenaltyPattern = '^([0-9]|([1-9][0-9])|100)$';
    // No dots allowed for the blackbox project type, because the folder naming works slightly different here.
    protected readonly packageNamePatternForJavaBlackbox =
        '^(?!.*(?:\\.|^)(?:abstract|continue|for|new|switch|assert|default|if|package|synchronized|boolean|do|goto|private|this|break|double|implements|protected|throw|byte|else|import|public|throws|case|enum|instanceof|return|transient|catch|extends|int|short|try|char|final|interface|static|void|class|finally|long|strictfp|volatile|const|float|native|super|while|_|true|false|null)(?:\\.|$))[A-Z_a-z][0-9A-Z_a-z]*$';
    // Auxiliary Repository names must only include words or '-' characters.
    protected readonly invalidRepositoryNamePattern = RegExp('^(?!(solution|exercise|tests|auxiliary)\\b)\\b(\\w|-)+$');
    // Auxiliary Repository checkout directories must be valid directory paths. Those must only include words,
    // '-' or '/' characters.
    protected readonly invalidDirectoryNamePattern = RegExp('^[\\w-]+(/[\\w-]+)*$');
    // length of < 3 is also accepted in order to provide more accurate validation error messages
    protected readonly shortNamePattern = RegExp('(^(?![\\s\\S]))|^[a-zA-Z][a-zA-Z0-9]*$|' + SHORT_NAME_PATTERN); // must start with a letter and cannot contain special characters
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faExclamationCircle = faExclamationCircle;

    // Java package name Regex according to Java 14 JLS (https://docs.oracle.com/javase/specs/jls/se14/html/jls-7.html#jls-7.4.1),
    // with the restriction to a-z,A-Z,_ as "Java letter" and 0-9 as digits due to JavaScript/Browser Unicode character class limitations
    readonly packageNamePatternForJavaKotlin =
        '^(?!.*(?:\\.|^)(?:abstract|continue|for|new|switch|assert|default|if|package|synchronized|boolean|do|goto|private|this|break|double|implements|protected|throw|byte|else|import|public|throws|case|enum|instanceof|return|transient|catch|extends|int|short|try|char|final|interface|static|void|class|finally|long|strictfp|volatile|const|float|native|super|while|_|true|false|null)(?:\\.|$))[A-Z_a-z][0-9A-Z_a-z]*(?:\\.[A-Z_a-z][0-9A-Z_a-z]*)*$';
    // Swift package name Regex derived from (https://docs.swift.org/swift-book/ReferenceManual/LexicalStructure.html#ID412),
    // with the restriction to a-z,A-Z as "Swift letter" and 0-9 as digits where no separators are allowed
    readonly appNamePatternForSwift =
        '^(?!(?:associatedtype|class|deinit|enum|extension|fileprivate|func|import|init|inout|internal|let|open|operator|private|protocol|public|rethrows|static|struct|subscript|typealias|var|break|case|continue|default|defer|do|else|fallthrough|for|guard|if|in|repeat|return|switch|where|while|as|Any|catch|false|is|nil|super|self|Self|throw|throws|true|try|_|[sS]wift)$)[A-Za-z][0-9A-Za-z]*$';

    @ViewChild(ProgrammingExerciseInformationComponent) exerciseInfoComponent?: ProgrammingExerciseInformationComponent;
    @ViewChild(ProgrammingExerciseModeComponent) exerciseDifficultyComponent?: ProgrammingExerciseModeComponent;
    @ViewChild(ProgrammingExerciseLanguageComponent) exerciseLanguageComponent?: ProgrammingExerciseLanguageComponent;
    @ViewChild(ProgrammingExerciseGradingComponent) exerciseGradingComponent?: ProgrammingExerciseGradingComponent;
    @ViewChild(ExerciseUpdatePlagiarismComponent) exercisePlagiarismComponent?: ExerciseUpdatePlagiarismComponent;

    packageNamePattern = '';
    isSimpleMode = signal<boolean>(true);

    isEditFieldDisplayedRecord = computed(() => {
        const inputFieldEditModeMapping = INPUT_FIELD_EDIT_MODE_MAPPING;

        const isEditFieldDisplayedMapping: Record<ProgrammingExerciseInputField, boolean> = {} as Record<ProgrammingExerciseInputField, boolean>;
        Object.keys(inputFieldEditModeMapping).forEach((key) => {
            const modeToBeIncluded = this.isSimpleMode() ? 'SIMPLE' : 'ADVANCED';
            // noinspection UnnecessaryLocalVariableJS: not inlined because the variable name improves readability
            const isDisplayed = inputFieldEditModeMapping[key as ProgrammingExerciseInputField].editModesToBeDisplayed.includes(modeToBeIncluded);
            isEditFieldDisplayedMapping[key as ProgrammingExerciseInputField] = isDisplayed;
        });

        return isEditFieldDisplayedMapping;
    });

    private translationBasePath = 'artemisApp.programmingExercise.';

    programmingLanguageChanged = (language: ProgrammingLanguage) => this.onProgrammingLanguageChange(language);
    withDependenciesChanged = (withDependencies: boolean) => this.onWithDependenciesChanged(withDependencies);
    categoriesChanged = (categories: ExerciseCategory[]) => this.updateCategories(categories);
    projectTypeChanged = (projectType: ProjectType) => this.onProjectTypeChange(projectType);
    staticCodeAnalysisChanged = () => this.onStaticCodeAnalysisChanged();

    auxiliaryRepositoryDuplicateNames: boolean;
    auxiliaryRepositoryDuplicateDirectories: boolean;
    auxiliaryRepositoryNamedCorrectly: boolean;
    isImportFromExistingExercise: boolean;
    isImportFromFile: boolean;
    isEdit: boolean;
    isExamMode: boolean;
    isLocal: boolean;
    hasUnsavedChanges = false;
    programmingExercise: ProgrammingExercise;
    backupExercise: ProgrammingExercise;
    isSaving: boolean;
    goBackAfterSaving = false;
    problemStatementLoaded = false;
    buildPlanLoaded = false;
    templateParticipationResultLoaded = true;
    notificationText?: string;
    courseId: number;

    rerenderSubject = new Subject<void>();
    // This is used to revert the select if the user cancels to override the new selected programming language.
    private selectedProgrammingLanguageValue: ProgrammingLanguage;
    // This is used to revert the select if the user cancels to override the new selected project type.
    private selectedProjectTypeValue?: ProjectType;

    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];

    formStatusSections: FormSectionStatus[];

    inputFieldSubscriptions: (Subscription | undefined)[] = [];

    public inProductionEnvironment: boolean;

    public supportedLanguages = ['java'];

    public packageNameRequired = true;
    public staticCodeAnalysisAllowed = false;
    public checkoutSolutionRepositoryAllowed = false;
    public customizeBuildPlanWithAeolus = false;
    public sequentialTestRunsAllowed = false;
    public testwiseCoverageAnalysisSupported = false;
    public auxiliaryRepositoriesSupported = false;
    public auxiliaryRepositoriesValid = true;
    public customBuildPlansSupported: string = '';
    public theiaEnabled = false;

    // Additional options for import
    // This is a wrapper to allow modifications from the other subcomponents
    public readonly importOptions: ImportOptions = {
        recreateBuildPlans: false,
        updateTemplate: false,
        setTestCaseVisibilityToAfterDueDate: false,
    };
    public originalStaticCodeAnalysisEnabled: boolean | undefined;

    public projectTypes?: ProjectType[] = [];
    // flag describing if the template and solution projects should include a dependency
    public withDependenciesValue = false;

    public modePickerOptions?: ModePickerOption<ProjectType>[] = [];

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
        private aeolusService: AeolusService,
    ) {
        effect(
            function updateStatusBarSectionsWhenEditModeChanges() {
                if (this.isSimpleMode()) {
                    this.calculateFormStatusSections();
                }
            }.bind(this),
        );
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
        if (!this.programmingExercise?.auxiliaryRepositories) {
            return;
        }

        let legalNameAndDirs = false;
        // Check that there are no duplicate names.
        const names = new Set<string | undefined>();
        const auxReposWithName = this.programmingExercise.auxiliaryRepositories?.filter((auxiliaryRepository) => auxiliaryRepository.name);
        auxReposWithName?.forEach((auxiliaryRepository) => {
            names.add(auxiliaryRepository.name);
            legalNameAndDirs ||= !this.invalidRepositoryNamePattern.test(auxiliaryRepository.name!);
        });
        this.auxiliaryRepositoryDuplicateNames = names.size !== auxReposWithName?.length;

        // Check that there are no duplicate checkout directories
        const directories = new Set<string | undefined>();
        const auxReposWithDirectory = this.programmingExercise.auxiliaryRepositories!.filter((auxiliaryRepository) => auxiliaryRepository.checkoutDirectory);
        auxReposWithDirectory.forEach((auxiliaryRepository) => {
            directories.add(auxiliaryRepository.checkoutDirectory);
            legalNameAndDirs ||= !this.invalidDirectoryNamePattern.test(auxiliaryRepository.checkoutDirectory!);
        });
        this.auxiliaryRepositoryDuplicateDirectories = directories.size !== auxReposWithDirectory.length;

        // Check that there are no empty/incorrect repository names and directories
        this.auxiliaryRepositoryNamedCorrectly = this.programmingExercise.auxiliaryRepositories!.length === auxReposWithName?.length && !legalNameAndDirs;

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
        this.packageNameRequired = programmingLanguageFeature?.packageNameRequired;
        this.staticCodeAnalysisAllowed = programmingLanguageFeature.staticCodeAnalysis;
        this.checkoutSolutionRepositoryAllowed = programmingLanguageFeature.checkoutSolutionRepositoryAllowed;
        this.sequentialTestRunsAllowed = programmingLanguageFeature.sequentialTestRuns;
        this.testwiseCoverageAnalysisSupported = programmingLanguageFeature.testwiseCoverageAnalysisSupported;
        this.auxiliaryRepositoriesSupported = programmingLanguageFeature.auxiliaryRepositoriesSupported;
        // filter out MAVEN_MAVEN and GRADLE_GRADLE because they are not directly selectable but only via a checkbox
        this.projectTypes = programmingLanguageFeature.projectTypes?.filter((projectType) => projectType !== ProjectType.MAVEN_MAVEN && projectType !== ProjectType.GRADLE_GRADLE);
        this.modePickerOptions = this.projectTypes?.map((projectType) => ({
            value: projectType,
            labelKey: 'artemisApp.programmingExercise.projectTypes.' + projectType.toString(),
            btnClass: 'btn-secondary',
        }));

        if (languageChanged) {
            // Reset project type when changing programming language as not all programming languages support (the same) project types
            this.programmingExercise.projectType = this.projectTypes?.[0];
            this.selectedProjectTypeValue = this.projectTypes?.[0];
            this.withDependenciesValue = false;
            this.buildPlanLoaded = false;
            if (this.programmingExercise.buildConfig) {
                this.programmingExercise.buildConfig.windfile = undefined;
                this.programmingExercise.buildConfig.buildPlanConfiguration = undefined;
            } else {
                this.programmingExercise.buildConfig = new ProgrammingExerciseBuildConfig();
            }
        }

        // If we switch to another language which does not support static code analysis we need to reset options related to static code analysis
        if (!this.staticCodeAnalysisAllowed) {
            this.programmingExercise.staticCodeAnalysisEnabled = false;
            this.programmingExercise.maxStaticCodeAnalysisPenalty = undefined;
        }

        if (language == ProgrammingLanguage.HASKELL || language == ProgrammingLanguage.OCAML) {
            // Instructors typically test against the example solution for Haskell and OCAML exercises.
            // If supported by the current CI configuration, this line activates the option per default.
            this.programmingExercise.buildConfig!.checkoutSolutionRepository = this.checkoutSolutionRepositoryAllowed;
        }
        if (!this.checkoutSolutionRepositoryAllowed) {
            this.programmingExercise.buildConfig!.checkoutSolutionRepository = false;
        }

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

    get selectedProjectType(): ProjectType | undefined {
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
            const programmingLanguageFeature = this.programmingLanguageFeatureService.getProgrammingLanguageFeature(ProgrammingLanguage.JAVA);
            if (type == ProjectType.MAVEN_BLACKBOX) {
                this.selectedProjectTypeValue = ProjectType.MAVEN_BLACKBOX;
                this.programmingExercise.projectType = ProjectType.MAVEN_BLACKBOX;
                this.sequentialTestRunsAllowed = false;
                this.testwiseCoverageAnalysisSupported = false;
            } else if (type === ProjectType.PLAIN_MAVEN || type === ProjectType.MAVEN_MAVEN) {
                this.selectedProjectTypeValue = ProjectType.PLAIN_MAVEN;
                this.sequentialTestRunsAllowed = programmingLanguageFeature.sequentialTestRuns;
                this.testwiseCoverageAnalysisSupported = programmingLanguageFeature.testwiseCoverageAnalysisSupported;
                if (this.withDependenciesValue) {
                    this.programmingExercise.projectType = ProjectType.MAVEN_MAVEN;
                } else {
                    this.programmingExercise.projectType = ProjectType.PLAIN_MAVEN;
                }
            } else {
                this.selectedProjectTypeValue = ProjectType.PLAIN_GRADLE;
                this.sequentialTestRunsAllowed = programmingLanguageFeature.sequentialTestRuns;
                this.testwiseCoverageAnalysisSupported = programmingLanguageFeature.testwiseCoverageAnalysisSupported;
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
            if (this.programmingExercise.buildConfig?.buildPlanConfiguration) {
                this.programmingExercise.buildConfig!.windfile = this.aeolusService.parseWindFile(this.programmingExercise.buildConfig!.buildPlanConfiguration);
            }
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
        if (this.activatedRoute && this.activatedRoute.url) {
            this.activatedRoute.url
                .pipe(
                    tap((segments) => {
                        this.isImportFromExistingExercise = segments.some((segment) => segment.path === 'import');
                        this.isImportFromFile = segments.some((segment) => segment.path === 'import-from-file');
                        this.isEdit = segments.some((segment) => segment.path === 'edit');
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
                                    if (!params['exerciseId'] && this.programmingExercise.exerciseGroup.exam?.course?.defaultProgrammingLanguage && !this.isImportFromFile) {
                                        this.selectedProgrammingLanguage = this.programmingExercise.exerciseGroup.exam.course.defaultProgrammingLanguage;
                                    }
                                });
                                // we need the course id  to make the request to the server if it's an import from file
                                if (this.isImportFromFile) {
                                    this.courseId = params['courseId'];
                                    this.loadCourseExerciseCategories(params['courseId']);
                                }
                            } else if (params['courseId']) {
                                this.courseId = params['courseId'];
                                this.isExamMode = false;
                                this.courseService.find(this.courseId).subscribe((res) => {
                                    this.programmingExercise.course = res.body!;
                                    if (!params['exerciseId'] && this.programmingExercise.course?.defaultProgrammingLanguage && !this.isImportFromFile) {
                                        this.selectedProgrammingLanguage = this.programmingExercise.course.defaultProgrammingLanguage!;
                                    }
                                    this.exerciseCategories = this.programmingExercise.categories || [];

                                    this.loadCourseExerciseCategories(this.programmingExercise.course!.id!);
                                });
                            }
                        }
                    }),
                )
                .subscribe();
        }

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

        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo?.activeProfiles.includes(PROFILE_LOCALCI)) {
                this.customBuildPlansSupported = PROFILE_LOCALCI;
                this.isLocal = true;
            }
            if (profileInfo?.activeProfiles.includes(PROFILE_AEOLUS)) {
                this.customBuildPlansSupported = PROFILE_AEOLUS;
            }
            if (profileInfo?.activeProfiles.includes(PROFILE_THEIA)) {
                this.theiaEnabled = true;
            }
        });
        this.defineSupportedProgrammingLanguages();
    }

    ngAfterViewInit() {
        this.inputFieldSubscriptions.push(this.exerciseInfoComponent?.formValidChanges?.subscribe(() => this.calculateFormStatusSections()));
        this.inputFieldSubscriptions.push(this.exerciseDifficultyComponent?.teamConfigComponent?.formValidChanges?.subscribe(() => this.calculateFormStatusSections()));
        this.inputFieldSubscriptions.push(this.exerciseLanguageComponent?.formValidChanges?.subscribe(() => this.calculateFormStatusSections()));
        this.inputFieldSubscriptions.push(this.exerciseGradingComponent?.formValidChanges?.subscribe(() => this.calculateFormStatusSections()));
        this.inputFieldSubscriptions.push(this.exercisePlagiarismComponent?.formValidChanges?.subscribe(() => this.calculateFormStatusSections()));
    }

    ngOnDestroy() {
        for (const subscription of this.inputFieldSubscriptions) {
            subscription?.unsubscribe();
        }
    }

    calculateFormStatusSections() {
        const updatedFormStatusSections = [
            {
                title: 'artemisApp.programmingExercise.wizardMode.detailedSteps.generalInfoStepTitle',
                valid: this.exerciseInfoComponent?.formValid ?? false,
            },
            {
                title: 'artemisApp.programmingExercise.wizardMode.detailedSteps.difficultyStepTitle',
                valid: (this.exerciseDifficultyComponent?.teamConfigComponent?.formValid && this.validIdeSelection()) ?? false,
            },
            {
                title: 'artemisApp.programmingExercise.wizardMode.detailedSteps.languageStepTitle',
                valid: (this.exerciseLanguageComponent?.formValid && this.validOnlineIdeSelection()) ?? false,
            },
            {
                title: 'artemisApp.programmingExercise.wizardMode.detailedSteps.problemStepTitle',
                valid: true,
                empty: !this.programmingExercise.problemStatement,
            },
            {
                title: 'artemisApp.programmingExercise.wizardMode.detailedSteps.gradingStepTitle',
                valid: Boolean(this.exerciseGradingComponent?.formValid && (this.isExamMode || this.exercisePlagiarismComponent?.formValid)),
                empty: this.exerciseGradingComponent?.formEmpty,
            },
        ];

        if (this.isSimpleMode()) {
            // the mode section would only contain the difficulty in the simple mode,
            // which is why the difficulty is moved to the general section instead
            const MODE_SECTION_INDEX = 1;
            updatedFormStatusSections.splice(MODE_SECTION_INDEX, MODE_SECTION_INDEX);
        }

        this.formStatusSections = updatedFormStatusSections;
    }

    private defineSupportedProgrammingLanguages() {
        this.supportedLanguages = [];

        for (const programmingLanguage of Object.values(ProgrammingLanguage)) {
            if (this.programmingLanguageFeatureService.supportsProgrammingLanguage(programmingLanguage)) {
                this.supportedLanguages.push(programmingLanguage);
            }
        }
    }

    private loadCourseExerciseCategories(courseId?: number) {
        loadCourseExerciseCategories(courseId, this.courseService, this.exerciseService, this.alertService).subscribe((existingCategories) => {
            this.existingCategories = existingCategories;
        });

        if (this.exerciseCategories === undefined) {
            this.exerciseCategories = [];
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
        const courseId = params['courseId'];
        if (courseId && params['examId'] && params['exerciseGroupId']) {
            this.exerciseGroupService.find(params['courseId'], params['examId'], params['exerciseGroupId']).subscribe((res) => {
                this.programmingExercise.exerciseGroup = res.body!;
                // Set course to undefined if a normal exercise is imported
                this.programmingExercise.course = undefined;
            });
            this.isExamMode = true;
        } else if (courseId) {
            this.courseService.find(courseId).subscribe((res) => {
                this.programmingExercise.course = res.body!;
                // Set exerciseGroup to undefined if an exam exercise is imported
                this.programmingExercise.exerciseGroup = undefined;
            });
            this.isExamMode = false;
        }
        this.loadCourseExerciseCategories(courseId);
        resetProgrammingForImport(this.programmingExercise);

        this.programmingExercise.projectKey = undefined;
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
        // trim potential whitespaces that can lead to issues
        if (this.programmingExercise.buildConfig!.windfile?.metadata?.docker?.image) {
            this.programmingExercise.buildConfig!.windfile.metadata.docker.image = this.programmingExercise.buildConfig!.windfile.metadata.docker.image.trim();
        }

        if (this.programmingExercise.customizeBuildPlanWithAeolus || this.isImportFromFile) {
            this.programmingExercise.buildConfig!.buildPlanConfiguration = this.aeolusService.serializeWindFile(this.programmingExercise.buildConfig!.windfile!);
        } else {
            this.programmingExercise.buildConfig!.buildPlanConfiguration = undefined;
            this.programmingExercise.buildConfig!.windfile = undefined;
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

        /*
         If properties for an auxiliary repository were edited, the changes have to be done manually in the VCS and CIS.
         Creating or deleting new auxiliary repositories works automatically and does not throw a warning.
         We check that by verifying all "current" repositories with an ID (meaning they are not new) with their backup.
         */
        const changedAuxiliaryRepositoryProperties = this.programmingExercise.auxiliaryRepositories?.some((auxRepo) => {
            // Ignore new auxiliary repositories
            if (!auxRepo.id) return false;

            // Verify unchanged properties for all existing auxiliary repositories
            return this.backupExercise?.auxiliaryRepositories?.some((backupAuxRepo) => {
                return backupAuxRepo.id === auxRepo.id && (backupAuxRepo.name !== auxRepo.name || backupAuxRepo.checkoutDirectory !== auxRepo.checkoutDirectory);
            });
        });
        if (changedAuxiliaryRepositoryProperties && this.programmingExercise.id) {
            this.alertService.addAlert({
                type: AlertType.WARNING,
                message: 'artemisApp.programmingExercise.auxiliaryRepository.editedWarning',
            });
        }
        if (this.isImportFromFile) {
            this.subscribeToSaveResponse(this.programmingExerciseService.importFromFile(this.programmingExercise, this.courseId));
        } else if (this.isImportFromExistingExercise) {
            this.subscribeToSaveResponse(this.programmingExerciseService.importExercise(this.programmingExercise, this.importOptions));
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
            next: (response: HttpResponse<ProgrammingExercise>) => {
                this.onSaveSuccess(response.body!);
            },
            error: (error: HttpErrorResponse) => {
                this.onSaveError(error);
            },
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
            this.importOptions.recreateBuildPlans = true;
            this.importOptions.updateTemplate = true;
        }

        if (!this.programmingExercise.staticCodeAnalysisEnabled) {
            this.programmingExercise.maxStaticCodeAnalysisPenalty = undefined;
        }
    }

    onRecreateBuildPlanOrUpdateTemplateChange() {
        if (!this.importOptions.recreateBuildPlans || !this.importOptions.updateTemplate) {
            this.programmingExercise.staticCodeAnalysisEnabled = this.originalStaticCodeAnalysisEnabled;
        }

        if (!this.programmingExercise.staticCodeAnalysisEnabled) {
            this.programmingExercise.maxStaticCodeAnalysisPenalty = undefined;
        }
    }

    protected switchEditMode = () => {
        this.isSimpleMode.update((isSimpleMode) => !isSimpleMode);
    };

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
     * checking if at least one of Online Editor, Offline Ide, or Online Ide is selected
     */
    validIdeSelection = () => {
        if (this.theiaEnabled) {
            return this.programmingExercise?.allowOnlineEditor || this.programmingExercise?.allowOfflineIde || this.programmingExercise?.allowOnlineIde;
        } else {
            return this.programmingExercise?.allowOnlineEditor || this.programmingExercise?.allowOfflineIde;
        }
    };

    /**
     * Checking if the online IDE is selected and a valid image is selected
     */
    validOnlineIdeSelection = () => {
        return !this.programmingExercise?.allowOnlineIde || this.programmingExercise?.buildConfig!.theiaImage !== undefined;
    };

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
        this.validateExerciseChannelName(validationErrorReasons);
        this.validateExerciseShortName(validationErrorReasons);
        this.validateExerciseAuxiliaryRepositories(validationErrorReasons);
        this.validateExercisePackageName(validationErrorReasons);
        this.validateExerciseIdeSelection(validationErrorReasons);
        this.validateExerciseOnlineIdeSelection(validationErrorReasons);
        this.validateExercisePoints(validationErrorReasons);
        this.validateExerciseBonusPoints(validationErrorReasons);
        this.validateExerciseSCAMaxPenalty(validationErrorReasons);
        this.validateExerciseSubmissionLimit(validationErrorReasons);

        return validationErrorReasons;
    }

    private validateExerciseTitle(validationErrorReasons: ValidationReason[]): void {
        if (this.programmingExercise.title === undefined || this.programmingExercise.title === '') {
            validationErrorReasons.push({
                translateKey: 'artemisApp.exercise.form.title.undefined',
                translateValues: {},
            });
        } else if (!EXERCISE_TITLE_NAME_REGEX.test(this.programmingExercise.title)) {
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

    private validateExerciseAuxiliaryRepositories(validationErrorReasons: ValidationReason[]): void {
        if (!this.auxiliaryRepositoriesValid) {
            validationErrorReasons.push({
                translateKey: 'artemisApp.programmingExercise.auxiliaryRepository.error',
                translateValues: {},
            });
        }
    }

    private validateExerciseIdeSelection(validationErrorReasons: ValidationReason[]): void {
        if (!this.validIdeSelection()) {
            const translateKey = this.theiaEnabled ? 'artemisApp.programmingExercise.allowOnlineEditor.alert' : 'artemisApp.programmingExercise.allowOnlineEditor.alertNoTheia';
            validationErrorReasons.push({
                translateKey: translateKey,
                translateValues: {},
            });
        }
    }

    private validateExerciseOnlineIdeSelection(validationErrorReasons: ValidationReason[]): void {
        if (!this.validOnlineIdeSelection()) {
            validationErrorReasons.push({
                translateKey: 'artemisApp.programmingExercise.theiaImage.alert',
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

        resetProgrammingForImport(this.programmingExercise);

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
            customBuildPlansSupported: this.customBuildPlansSupported,
            invalidDirectoryNamePattern: this.invalidDirectoryNamePattern,
            invalidRepositoryNamePattern: this.invalidRepositoryNamePattern,
            titleNamePattern: EXERCISE_TITLE_NAME_PATTERN,
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
            validOnlineIdeSelection: this.validOnlineIdeSelection,
            inProductionEnvironment: this.inProductionEnvironment,
            recreateBuildPlans: this.importOptions.recreateBuildPlans,
            onRecreateBuildPlanOrUpdateTemplateChange: this.onRecreateBuildPlanOrUpdateTemplateChange,
            updateTemplate: this.importOptions.updateTemplate,
            recreateBuildPlanOrUpdateTemplateChange: this.onRecreateBuildPlanOrUpdateTemplateChange,
            buildPlanLoaded: this.buildPlanLoaded,
        };
    }
}
