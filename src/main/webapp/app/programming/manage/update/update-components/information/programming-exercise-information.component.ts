import { AfterViewInit, Component, OnDestroy, OnInit, computed, effect, inject, input, model, signal, viewChild, viewChildren } from '@angular/core';
import { FormsModule, NgModel } from '@angular/forms';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROGRAMMING_EXERCISE_SHORT_NAME_MAX_LENGTH } from 'app/foundation/constants/input.constants';
import { ProgrammingExercise, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent } from 'app/programming/shared/build-details/programming-exercise-repository-and-build-plan-details/programming-exercise-repository-and-build-plan-details.component';
import { ExerciseTitleChannelNameComponent } from 'app/exercise/exercise-title-channel-name/exercise-title-channel-name.component';
import { Subject, Subscription } from 'rxjs';
import { TableEditableFieldComponent } from 'app/shared-ui/table/editable-field/table-editable-field.component';
import { every } from 'lodash-es';
import { ImportOptions } from 'app/programming/manage/programming-exercises';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { removeSpecialCharacters } from 'app/foundation/util/utils';
import { CourseExistingExerciseDetailsType, ExerciseService } from 'app/exercise/services/exercise.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseEditCheckoutDirectoriesComponent } from 'app/programming/shared/build-details/programming-exercise-edit-checkout-directories/programming-exercise-edit-checkout-directories.component';
import { BuildPlanCheckoutDirectoriesDTO } from 'app/programming/shared/entities/build-plan-checkout-directories-dto';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { CustomNotIncludedInValidatorDirective } from 'app/foundation/validators/custom-not-included-in-validator.directive';
import { RemoveAuxiliaryRepositoryButtonComponent } from '../../remove-auxiliary-repository-button.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { AddAuxiliaryRepositoryButtonComponent } from '../../add-auxiliary-repository-button.component';
import { CategorySelectorPrimengComponent } from 'app/exercise/category-selector-primeng/category-selector-primeng.component';
import { ProgrammingExerciseDifficultyComponent } from '../difficulty/programming-exercise-difficulty.component';
import { KeyValuePipe } from '@angular/common';
import { RemoveKeysPipe } from 'app/foundation/pipes/remove-keys.pipe';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';
import { Message } from 'primeng/message';

const MAXIMUM_TRIES_TO_GENERATE_UNIQUE_SHORT_NAME = 200;

@Component({
    selector: 'jhi-programming-exercise-info',
    templateUrl: './programming-exercise-information.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss', 'programming-exercise-information.component.scss'],
    imports: [
        TranslateDirective,
        HelpIconComponent,
        ExerciseTitleChannelNameComponent,
        FormsModule,
        CustomNotIncludedInValidatorDirective,
        ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent,
        ProgrammingExerciseEditCheckoutDirectoriesComponent,
        TableEditableFieldComponent,
        RemoveAuxiliaryRepositoryButtonComponent,
        ButtonComponent,
        AddAuxiliaryRepositoryButtonComponent,
        CategorySelectorPrimengComponent,
        ProgrammingExerciseDifficultyComponent,
        KeyValuePipe,
        RemoveKeysPipe,
        TableViewComponent,
        Message,
    ],
})
export class ProgrammingExerciseInformationComponent implements AfterViewInit, OnInit, OnDestroy {
    protected readonly ProjectType = ProjectType;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly faPlus = faPlus;
    protected readonly PROGRAMMING_EXERCISE_SHORT_NAME_MAX_LENGTH = PROGRAMMING_EXERCISE_SHORT_NAME_MAX_LENGTH;

    programmingExerciseCreationConfig = input.required<ProgrammingExerciseCreationConfig>();
    isImport = input.required<boolean>();
    isExamMode = input.required<boolean>();
    programmingExercise = input.required<ProgrammingExercise>();
    importOptions = input.required<ImportOptions>();
    isSimpleMode = input.required<boolean>();
    isEditFieldDisplayedRecord = input.required<Record<ProgrammingExerciseInputField, boolean>>();
    courseId = input<number>();
    isAuxiliaryRepositoryInputValid = model.required<boolean>();

    exerciseTitleChannelComponent = viewChild.required(ExerciseTitleChannelNameComponent);
    tableEditableFields = viewChildren(TableEditableFieldComponent);

    shortNameField = viewChild<NgModel>('shortName');
    checkoutSolutionRepositoryField = viewChild<NgModel>('checkoutSolutionRepository');
    recreateBuildPlansField = viewChild<NgModel>('recreateBuildPlans');
    updateTemplateFilesField = viewChild<NgModel>('updateTemplateFiles');
    programmingExerciseEditCheckoutDirectories = viewChild(ProgrammingExerciseEditCheckoutDirectoriesComponent);

    readonly auxRepoNameTemplate = viewChild<CellTemplateRef<AuxiliaryRepository>>('auxRepoNameTemplate');
    readonly auxCheckoutDirTemplate = viewChild<CellTemplateRef<AuxiliaryRepository>>('auxCheckoutDirTemplate');
    readonly auxDescriptionTemplate = viewChild<CellTemplateRef<AuxiliaryRepository>>('auxDescriptionTemplate');

    readonly auxiliaryRepoTableOptions: TableViewOptions = {
        lazy: false,
        paginated: false,
        showSearch: false,
        striped: true,
    };

    readonly auxiliaryRepoColumns = computed<ColumnDef<AuxiliaryRepository>[]>(() => [
        {
            field: 'name',
            headerKey: 'artemisApp.programmingExercise.auxiliaryRepository.repositoryName',
            templateRef: this.auxRepoNameTemplate(),
        },
        {
            field: 'checkoutDirectory',
            headerKey: 'artemisApp.programmingExercise.auxiliaryRepository.checkoutDirectory',
            templateRef: this.auxCheckoutDirTemplate(),
        },
        {
            field: 'description',
            headerKey: 'artemisApp.programmingExercise.auxiliaryRepository.description',
            templateRef: this.auxDescriptionTemplate(),
        },
    ]);

    private readonly exerciseService = inject(ExerciseService);
    private readonly alertService = inject(AlertService);
    private readonly profileService = inject(ProfileService);

    isShortNameFieldValid = signal<boolean>(false);
    isShortNameFromAdvancedMode = signal<boolean>(false);

    formValid: boolean;
    formValidChanges = new Subject<boolean>();

    inputFieldSubscriptions: (Subscription | undefined)[] = [];

    alreadyUsedExerciseNames = signal<Set<string>>(new Set());
    alreadyUsedShortNames = signal<Set<string>>(new Set());

    exerciseTitle = signal<string | undefined>(undefined);

    readonly editRepositoryCheckoutPath = signal(false);
    readonly submissionBuildPlanCheckoutRepositories = signal<BuildPlanCheckoutDirectoriesDTO>(undefined!);

    readonly isLocalCIEnabled = signal(true);

    constructor() {
        effect(() => {
            // Mirror the former ngOnChanges(programmingExercise): seed the working title from the bound exercise.
            this.exerciseTitle.set(this.programmingExercise().title);
        });

        effect(() => {
            this.defineShortNameOnEditModeChangeIfNotDefinedInAdvancedMode();
        });

        effect(() => {
            this.generateShortNameWhenInSimpleMode();
        });

        effect(() => {
            this.registerInputFieldsWhenChildComponentsAreReady();
        });

        effect(() => {
            this.fetchAndInitializeTakenTitlesAndShortNames();
        });

        effect(() => {
            this.updateFormStatus();
        });
    }

    private updateFormStatus() {
        this.exerciseTitleChannelComponent()?.titleChannelNameComponent()?.isValid(); // triggers effect on change

        this.calculateFormValid();
    }

    ngOnInit() {
        this.isLocalCIEnabled.set(this.profileService.isProfileActive(PROFILE_LOCALCI));
    }

    ngAfterViewInit() {
        this.registerInputFields();
    }

    ngOnDestroy(): void {
        for (const subscription of this.inputFieldSubscriptions) {
            subscription?.unsubscribe();
        }
    }

    registerInputFields() {
        this.inputFieldSubscriptions.forEach((subscription) => subscription?.unsubscribe());

        this.inputFieldSubscriptions.push(this.shortNameField()?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.checkoutSolutionRepositoryField()?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.recreateBuildPlansField()?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.updateTemplateFilesField()?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.programmingExerciseEditCheckoutDirectories()?.formValidChanges.subscribe(() => this.calculateFormValid()));
        // viewChildren() is a signal of the current list; subscribe directly. Re-registration when the
        // list changes is driven by the registerInputFieldsWhenChildComponentsAreReady effect, which reads
        // tableEditableFields() and re-invokes this method.
        this.tableEditableFields().forEach((field) => this.inputFieldSubscriptions.push(field.editingInput?.valueChanges?.subscribe(() => this.calculateFormValid())));

        // Title changes are handled via the (onTitleChange) output binding of jhi-exercise-title-channel-name in the
        // template (see updateShortName). A manual subscription to the deeply-nested title NgModel is not reliable here: it
        // is only registered once and the nested viewChild may not be resolved yet, so under zoneless change detection the
        // auto-generated short name was never updated in simple mode.

        this.shortNameField()?.valueChanges?.subscribe(() => {
            this.updateIsShortNameValid();
        });
    }

    /**
     * Reacts to a title change emitted by the title/channel-name component. Updating {@link exerciseTitle} re-triggers the
     * short-name generation effect, which (in simple mode) derives a unique short name from the new title.
     * @param newTitle the new exercise title
     */
    updateShortName(newTitle: string) {
        this.exerciseTitle.set(newTitle);
    }

    calculateFormValid() {
        const isCheckoutSolutionRepositoryValid = this.isCheckoutSolutionRepositoryValid();
        const isRecreateBuildPlansValid = this.isRecreateBuildPlansValid();
        const isUpdateTemplateFilesValid = this.isUpdateTemplateFilesValid();
        const areAuxiliaryRepositoriesValid = this.areAuxiliaryRepositoriesValid();
        const areCheckoutPathsValid = this.areCheckoutPathsValid();
        this.formValid = Boolean(
            this.exerciseTitleChannelComponent().titleChannelNameComponent().isValid() &&
            this.getIsShortNameFieldValid() &&
            isCheckoutSolutionRepositoryValid &&
            isRecreateBuildPlansValid &&
            isUpdateTemplateFilesValid &&
            areAuxiliaryRepositoriesValid &&
            areCheckoutPathsValid,
        );
        this.formValidChanges.next(this.formValid);
    }

    areAuxiliaryRepositoriesValid(): boolean {
        const areAuxiliaryRepositoriesValid =
            (every(
                this.tableEditableFields().map((field) => field.editingInput?.valid),
                Boolean,
            ) &&
                !this.programmingExerciseCreationConfig().auxiliaryRepositoryDuplicateDirectories &&
                !this.programmingExerciseCreationConfig().auxiliaryRepositoryDuplicateNames) ||
            !this.programmingExercise().auxiliaryRepositories?.length;

        const isAuxRepoEditingPossibleInCurrentEditMode = !this.isSimpleMode() || this.isEditFieldDisplayedRecord().addAuxiliaryRepository;
        if (isAuxRepoEditingPossibleInCurrentEditMode) {
            // if editing is not possible, the field will not be displayed and validity checks will evaluate to true,
            // even if the actual current setting is invalid
            this.isAuxiliaryRepositoryInputValid.set(areAuxiliaryRepositoriesValid);
        }
        return areAuxiliaryRepositoriesValid;
    }

    isUpdateTemplateFilesValid(): boolean {
        return (
            this.updateTemplateFilesField()?.valid ||
            !this.programmingExerciseCreationConfig().isImportFromExistingExercise ||
            this.programmingExercise().projectType === ProjectType.PLAIN_GRADLE ||
            this.programmingExercise().projectType === ProjectType.GRADLE_GRADLE
        );
    }

    isRecreateBuildPlansValid(): boolean {
        return this.recreateBuildPlansField()?.valid || !this.programmingExerciseCreationConfig().isImportFromExistingExercise;
    }

    isCheckoutSolutionRepositoryValid(): boolean {
        const repositoryFieldValid = this.checkoutSolutionRepositoryField()?.valid || this.programmingExercise().id;
        const repositoryNotApplicable = !this.programmingExercise().programmingLanguage || !this.programmingExerciseCreationConfig().checkoutSolutionRepositoryAllowed;
        return Boolean(repositoryFieldValid || repositoryNotApplicable);
    }

    areCheckoutPathsValid(): boolean {
        const editCheckoutDirectories = this.programmingExerciseEditCheckoutDirectories();
        if (!editCheckoutDirectories) {
            return true;
        }
        const checkoutPaths = [
            this.programmingExercise().buildConfig?.assignmentCheckoutPath,
            this.programmingExercise().buildConfig?.testCheckoutPath,
            this.programmingExercise().buildConfig?.solutionCheckoutPath,
        ];
        return Boolean(editCheckoutDirectories.formValid() && editCheckoutDirectories.areValuesUnique(checkoutPaths));
    }

    toggleEditRepositoryCheckoutPath() {
        this.editRepositoryCheckoutPath.update((editRepositoryCheckoutPath) => !editRepositoryCheckoutPath);
    }

    updateSubmissionBuildPlanCheckoutDirectories(buildPlanCheckoutDirectoriesDTO: BuildPlanCheckoutDirectoriesDTO) {
        this.submissionBuildPlanCheckoutRepositories.set(buildPlanCheckoutDirectoriesDTO);
    }

    onAssigmentRepositoryCheckoutPathChange(event: string) {
        this.programmingExercise().buildConfig!.assignmentCheckoutPath = event;
        // We need to create a new object to trigger the change detection
        this.programmingExercise().buildConfig = { ...this.programmingExercise().buildConfig! };
    }

    onTestRepositoryCheckoutPathChange(event: string) {
        this.programmingExercise().buildConfig!.testCheckoutPath = event;
        // We need to create a new object to trigger the change detection
        this.programmingExercise().buildConfig = { ...this.programmingExercise().buildConfig! };
    }

    onSolutionRepositoryCheckoutPathChange(event: string) {
        this.programmingExercise().buildConfig!.solutionCheckoutPath = event;
        // We need to create a new object to trigger the change detection
        this.programmingExercise().buildConfig = { ...this.programmingExercise().buildConfig! };
    }

    private registerInputFieldsWhenChildComponentsAreReady() {
        this.shortNameField(); // triggers effect on change
        this.tableEditableFields(); // re-register when the set of auxiliary-repository fields changes
        this.registerInputFields();
    }

    private fetchAndInitializeTakenTitlesAndShortNames() {
        const courseId = this.courseId() ?? this.programmingExercise().course?.id;
        if (courseId) {
            this.exerciseService.getExistingExerciseDetailsInCourse(courseId, ExerciseType.PROGRAMMING).subscribe((exerciseDetails: CourseExistingExerciseDetailsType) => {
                this.alreadyUsedExerciseNames.set(exerciseDetails.exerciseTitles ?? new Set());
                this.alreadyUsedShortNames.set(exerciseDetails.shortNames ?? new Set());
            });
        }
    }

    private generateShortNameWhenInSimpleMode() {
        const shouldNotGenerateShortName = !this.isSimpleMode() || this.programmingExerciseCreationConfig().isEdit;
        if (shouldNotGenerateShortName) {
            this.isShortNameFromAdvancedMode.set(this.isShortNameFieldValid());
            return;
        }
        let newShortName = this.exerciseTitle() ?? this.programmingExercise().title;
        if (this.isImport() || this.isShortNameFromAdvancedMode()) {
            newShortName = this.programmingExercise().shortName;
        }

        if (newShortName && newShortName.length > 3) {
            const sanitizedShortName = removeSpecialCharacters(newShortName).slice(0, PROGRAMMING_EXERCISE_SHORT_NAME_MAX_LENGTH);
            // noinspection UnnecessaryLocalVariableJS: not inlined because the variable name improves readability
            const uniqueShortName = this.ensureShortNameIsUnique(sanitizedShortName);
            this.programmingExercise().shortName = uniqueShortName;
        }

        this.updateIsShortNameValid();
    }

    private defineShortNameOnEditModeChangeIfNotDefinedInAdvancedMode() {
        if (this.isSimpleMode()) {
            this.updateIsShortNameValid();
            this.calculateFormValid();
        }
    }

    private updateIsShortNameValid() {
        this.isShortNameFieldValid.set(this.getIsShortNameFieldValid());
    }

    private getIsShortNameFieldValid() {
        return this.shortNameField() === undefined || this.shortNameField()?.control?.status === 'VALID' || this.shortNameField()?.control?.status === 'DISABLED';
    }

    private ensureShortNameIsUnique(shortName: string): string {
        let newShortName = shortName;
        let counter = 1;
        while (this.alreadyUsedShortNames().has(newShortName)) {
            if (counter > MAXIMUM_TRIES_TO_GENERATE_UNIQUE_SHORT_NAME) {
                this.alertService.error('artemisApp.error.shortNameGenerationFailed');
                break;
            }
            // Truncate the base so that base + counter suffix still fits within the max length.
            const suffix = `${counter}`;
            const truncatedBase = shortName.slice(0, PROGRAMMING_EXERCISE_SHORT_NAME_MAX_LENGTH - suffix.length);
            newShortName = `${truncatedBase}${suffix}`;
            counter++;
        }
        return newShortName;
    }
}
