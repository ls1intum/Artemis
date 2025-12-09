import {
    AfterViewInit,
    Component,
    Input,
    OnChanges,
    OnDestroy,
    OnInit,
    QueryList,
    SimpleChanges,
    ViewChild,
    ViewChildren,
    effect,
    inject,
    input,
    model,
    signal,
    viewChild,
} from '@angular/core';
import { FormsModule, NgModel } from '@angular/forms';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProgrammingExercise, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent } from 'app/programming/shared/build-details/programming-exercise-repository-and-build-plan-details/programming-exercise-repository-and-build-plan-details.component';
import { ExerciseTitleChannelNameComponent } from 'app/exercise/exercise-title-channel-name/exercise-title-channel-name.component';
import { Subject, Subscription } from 'rxjs';
import { TableEditableFieldComponent } from 'app/shared/table/editable-field/table-editable-field.component';
import { every } from 'lodash-es';
import { ImportOptions } from 'app/programming/manage/programming-exercises';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { removeSpecialCharacters } from 'app/shared/util/utils';
import { CourseExistingExerciseDetailsType, ExerciseService } from 'app/exercise/services/exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseEditCheckoutDirectoriesComponent } from 'app/programming/shared/build-details/programming-exercise-edit-checkout-directories/programming-exercise-edit-checkout-directories.component';
import { BuildPlanCheckoutDirectoriesDTO } from 'app/programming/shared/entities/build-plan-checkout-directories-dto';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { CustomNotIncludedInValidatorDirective } from 'app/shared/validators/custom-not-included-in-validator.directive';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { RemoveAuxiliaryRepositoryButtonComponent } from '../../remove-auxiliary-repository-button.component';
import { NgbAlert } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { AddAuxiliaryRepositoryButtonComponent } from '../../add-auxiliary-repository-button.component';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { ProgrammingExerciseDifficultyComponent } from '../difficulty/programming-exercise-difficulty.component';
import { KeyValuePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';

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
        NgxDatatableModule,
        TableEditableFieldComponent,
        RemoveAuxiliaryRepositoryButtonComponent,
        NgbAlert,
        ButtonComponent,
        AddAuxiliaryRepositoryButtonComponent,
        CategorySelectorComponent,
        ProgrammingExerciseDifficultyComponent,
        KeyValuePipe,
        ArtemisTranslatePipe,
        RemoveKeysPipe,
    ],
})
export class ProgrammingExerciseInformationComponent implements AfterViewInit, OnInit, OnChanges, OnDestroy {
    protected readonly ProjectType = ProjectType;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly faPlus = faPlus;

    @Input({ required: true }) programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    isImport = input.required<boolean>();
    isExamMode = input.required<boolean>();
    programmingExercise = input.required<ProgrammingExercise>();
    importOptions = input.required<ImportOptions>();
    isSimpleMode = input.required<boolean>();
    isEditFieldDisplayedRecord = input.required<Record<ProgrammingExerciseInputField, boolean>>();
    courseId = input<number>();
    isAuxiliaryRepositoryInputValid = model.required<boolean>();

    exerciseTitleChannelComponent = viewChild.required(ExerciseTitleChannelNameComponent);
    @ViewChildren(TableEditableFieldComponent) tableEditableFields?: QueryList<TableEditableFieldComponent>;

    shortNameField = viewChild<NgModel>('shortName');
    @ViewChild('checkoutSolutionRepository') checkoutSolutionRepositoryField?: NgModel;
    @ViewChild('recreateBuildPlans') recreateBuildPlansField?: NgModel;
    @ViewChild('updateTemplateFiles') updateTemplateFilesField?: NgModel;
    @ViewChild(ProgrammingExerciseEditCheckoutDirectoriesComponent) programmingExerciseEditCheckoutDirectories?: ProgrammingExerciseEditCheckoutDirectoriesComponent;

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

    editRepositoryCheckoutPath = false;
    submissionBuildPlanCheckoutRepositories: BuildPlanCheckoutDirectoriesDTO;

    isLocalCIEnabled = true;

    constructor() {
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
        this.isLocalCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
    }

    ngAfterViewInit() {
        this.registerInputFields();
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.programmingExercise) {
            this.exerciseTitle.set(this.programmingExercise().title);
        }
    }

    ngOnDestroy(): void {
        for (const subscription of this.inputFieldSubscriptions) {
            subscription?.unsubscribe();
        }
    }

    registerInputFields() {
        this.inputFieldSubscriptions.forEach((subscription) => subscription?.unsubscribe());

        this.inputFieldSubscriptions.push(this.shortNameField()?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.checkoutSolutionRepositoryField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.recreateBuildPlansField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.updateTemplateFilesField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.programmingExerciseEditCheckoutDirectories?.formValidChanges.subscribe(() => this.calculateFormValid()));
        this.tableEditableFields?.changes.subscribe((fields: QueryList<TableEditableFieldComponent>) => {
            fields.toArray().forEach((field) => this.inputFieldSubscriptions.push(field.editingInput.valueChanges?.subscribe(() => this.calculateFormValid())));
        });

        this.exerciseTitleChannelComponent()
            .titleChannelNameComponent()
            .field_title?.valueChanges?.subscribe((newTitle: string) => {
                if (this.isSimpleMode()) {
                    this.updateShortName(newTitle);
                }
            });

        this.shortNameField()?.valueChanges?.subscribe(() => {
            this.updateIsShortNameValid();
        });
    }

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
                this.tableEditableFields?.map((field) => field.editingInput.valid),
                Boolean,
            ) &&
                !this.programmingExerciseCreationConfig.auxiliaryRepositoryDuplicateDirectories &&
                !this.programmingExerciseCreationConfig.auxiliaryRepositoryDuplicateNames) ||
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
            this.updateTemplateFilesField?.valid ||
            !this.programmingExerciseCreationConfig.isImportFromExistingExercise ||
            this.programmingExercise().projectType === ProjectType.PLAIN_GRADLE ||
            this.programmingExercise().projectType === ProjectType.GRADLE_GRADLE
        );
    }

    isRecreateBuildPlansValid(): boolean {
        return this.recreateBuildPlansField?.valid || !this.programmingExerciseCreationConfig.isImportFromExistingExercise;
    }

    isCheckoutSolutionRepositoryValid(): boolean {
        return Boolean(
            this.checkoutSolutionRepositoryField?.valid ||
            this.programmingExercise().id ||
            !this.programmingExercise().programmingLanguage ||
            !this.programmingExerciseCreationConfig.checkoutSolutionRepositoryAllowed,
        );
    }

    areCheckoutPathsValid(): boolean {
        return Boolean(
            !this.programmingExerciseEditCheckoutDirectories ||
            (this.programmingExerciseEditCheckoutDirectories.formValid &&
                this.programmingExerciseEditCheckoutDirectories.areValuesUnique([
                    this.programmingExercise().buildConfig?.assignmentCheckoutPath,
                    this.programmingExercise().buildConfig?.testCheckoutPath,
                    this.programmingExercise().buildConfig?.solutionCheckoutPath,
                ])),
        );
    }

    toggleEditRepositoryCheckoutPath() {
        this.editRepositoryCheckoutPath = !this.editRepositoryCheckoutPath;
    }

    updateSubmissionBuildPlanCheckoutDirectories(buildPlanCheckoutDirectoriesDTO: BuildPlanCheckoutDirectoriesDTO) {
        this.submissionBuildPlanCheckoutRepositories = buildPlanCheckoutDirectoriesDTO;
    }

    onAssigmentRepositoryCheckoutPathChange(event: string) {
        this.programmingExercise().buildConfig!.assignmentCheckoutPath = event;
        // We need to create a new object to trigger the change detection
        this.programmingExercise().buildConfig = Object.assign({}, this.programmingExercise().buildConfig!);
    }

    onTestRepositoryCheckoutPathChange(event: string) {
        this.programmingExercise().buildConfig!.testCheckoutPath = event;
        // We need to create a new object to trigger the change detection
        this.programmingExercise().buildConfig = Object.assign({}, this.programmingExercise().buildConfig!);
    }

    onSolutionRepositoryCheckoutPathChange(event: string) {
        this.programmingExercise().buildConfig!.solutionCheckoutPath = event;
        // We need to create a new object to trigger the change detection
        this.programmingExercise().buildConfig = Object.assign({}, this.programmingExercise().buildConfig!);
    }

    private registerInputFieldsWhenChildComponentsAreReady() {
        this.shortNameField(); // triggers effect on change
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
        const shouldNotGenerateShortName = !this.isSimpleMode() || this.programmingExerciseCreationConfig.isEdit;
        if (shouldNotGenerateShortName) {
            this.isShortNameFromAdvancedMode.set(this.isShortNameFieldValid());
            return;
        }
        let newShortName = this.exerciseTitle() ?? this.programmingExercise().title;
        if (this.isImport() || this.isShortNameFromAdvancedMode()) {
            newShortName = this.programmingExercise().shortName;
        }

        if (newShortName && newShortName.length > 3) {
            const sanitizedShortName = removeSpecialCharacters(newShortName ?? '');
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
            newShortName = `${shortName}${counter}`;
            counter++;
        }
        return newShortName;
    }
}
