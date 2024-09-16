import { AfterViewInit, Component, Input, OnChanges, OnDestroy, QueryList, SimpleChanges, ViewChild, ViewChildren, effect, input, signal, viewChild } from '@angular/core';
import { NgModel } from '@angular/forms';
import { ProgrammingExercise, ProjectType } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { ExerciseTitleChannelNameComponent } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.component';
import { Subject, Subscription } from 'rxjs';
import { TableEditableFieldComponent } from 'app/shared/table/table-editable-field.component';
import { every } from 'lodash-es';
import { ImportOptions } from 'app/types/programming-exercises';
import { ProgrammingExerciseInputField } from 'app/exercises/programming/manage/update/programming-exercise-update.helper';
import { removeSpecialCharacters } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-programming-exercise-info',
    templateUrl: './programming-exercise-information.component.html',
    styleUrls: ['../../../programming-exercise-form.scss', 'programming-exercise-information.component.scss'],
})
export class ProgrammingExerciseInformationComponent implements AfterViewInit, OnChanges, OnDestroy {
    protected readonly ProjectType = ProjectType;

    @Input({ required: true }) programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    isImport = input.required<boolean>();
    isExamMode = input.required<boolean>();
    programmingExercise = input.required<ProgrammingExercise>();
    isLocal = input.required<boolean>();
    importOptions = input.required<ImportOptions>();
    isSimpleMode = input.required<boolean>();
    isEditFieldDisplayedRecord = input.required<Record<ProgrammingExerciseInputField, boolean>>();

    exerciseTitleChannelComponent = viewChild<ExerciseTitleChannelNameComponent>('titleChannelNameComponent');
    @ViewChildren(TableEditableFieldComponent) tableEditableFields?: QueryList<TableEditableFieldComponent>;

    shortNameField = viewChild<NgModel>('shortName');
    @ViewChild('checkoutSolutionRepository') checkoutSolutionRepositoryField?: NgModel;
    @ViewChild('recreateBuildPlans') recreateBuildPlansField?: NgModel;
    @ViewChild('updateTemplateFiles') updateTemplateFilesField?: NgModel;
    @ViewChild('titleChannelNameComponent') titleComponent?: ExerciseTitleChannelNameComponent;

    isShortNameFieldValid = signal<boolean>(false);

    formValid: boolean;
    formValidChanges = new Subject<boolean>();

    inputFieldSubscriptions: (Subscription | undefined)[] = [];

    exerciseTitle = signal<string | undefined>(undefined);
    shortNameRandomPart = signal<string>(this.generateRandomShortNameLetters());

    constructor() {
        effect(
            function generateShortNameWhenInSimpleMode() {
                const shouldNotGenerateShortName = !this.isSimpleMode() || this.programmingExerciseCreationConfig.isEdit;
                if (shouldNotGenerateShortName) {
                    return;
                }
                let newShortName = this.exerciseTitle();
                const isImport = this.programmingExerciseCreationConfig.isImportFromFile || this.programmingExerciseCreationConfig.isImportFromExistingExercise;
                if (isImport) {
                    newShortName = this.programmingExercise().shortName;
                }

                if (newShortName && newShortName.length > 3) {
                    const sanitizedShortName = removeSpecialCharacters(newShortName ?? '').substring(0, 6);
                    // noinspection UnnecessaryLocalVariableJS: not inlined because the variable name improves readability
                    const shortnameWithRandomness = sanitizedShortName + this.shortNameRandomPart();
                    this.programmingExercise().shortName = shortnameWithRandomness;
                }

                this.updateShortNameSignal();
            }.bind(this),
            { allowSignalWrites: true },
        );

        effect(() => {
            if (this.shortNameField() || this.exerciseTitleChannelComponent()) {
            } // triggers effect
            this.registerInputFields();
        });
    }

    registerInputFields() {
        this.inputFieldSubscriptions.forEach((subscription) => subscription?.unsubscribe());

        this.inputFieldSubscriptions.push(this.exerciseTitleChannelComponent()?.titleChannelNameComponent?.formValidChanges.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.shortNameField()?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.checkoutSolutionRepositoryField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.recreateBuildPlansField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.updateTemplateFilesField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.tableEditableFields?.changes.subscribe((fields: QueryList<TableEditableFieldComponent>) => {
            fields.toArray().forEach((field) => this.inputFieldSubscriptions.push(field.editingInput.valueChanges?.subscribe(() => this.calculateFormValid())));
        });

        this.titleComponent?.titleChannelNameComponent?.field_title?.valueChanges?.subscribe((newTitle: string) => {
            if (this.isSimpleMode()) {
                this.updateShortName(newTitle);
            }
        });

        this.shortNameField()?.valueChanges?.subscribe(() => {
            this.updateShortNameSignal();
        });
    }

    ngAfterViewInit() {
        this.registerInputFields();
    }

    updateShortName(newTitle: string) {
        this.exerciseTitle.set(newTitle);
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

    calculateFormValid() {
        const isCheckoutSolutionRepositoryValid = this.isCheckoutSolutionRepositoryValid();
        const isRecreateBuildPlansValid = this.isRecreateBuildPlansValid();
        const isUpdateTemplateFilesValid = this.isUpdateTemplateFilesValid();
        const areAuxiliaryRepositoriesValid = this.areAuxiliaryRepositoriesValid();
        this.formValid = Boolean(
            this.exerciseTitleChannelComponent()?.titleChannelNameComponent?.formValidSignal() &&
                this.isShortNameFieldValid() &&
                isCheckoutSolutionRepositoryValid &&
                isRecreateBuildPlansValid &&
                isUpdateTemplateFilesValid &&
                areAuxiliaryRepositoriesValid,
        );
        this.formValidChanges.next(this.formValid);
    }

    areAuxiliaryRepositoriesValid(): boolean {
        return (
            (every(
                this.tableEditableFields?.map((field) => field.editingInput.valid),
                Boolean,
            ) &&
                !this.programmingExerciseCreationConfig.auxiliaryRepositoryDuplicateDirectories &&
                !this.programmingExerciseCreationConfig.auxiliaryRepositoryDuplicateNames) ||
            !this.programmingExercise().auxiliaryRepositories?.length
        );
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

    private updateShortNameSignal() {
        this.isShortNameFieldValid.set(this.shortNameField() === undefined || this.shortNameField()?.control?.status === 'VALID');
    }

    private generateRandomShortNameLetters(): string {
        const alphabet = 'abcdefghijklmnopqrstuvwxyz';
        const LENGTH_OF_RANDOM_STRING = 3;
        // noinspection UnnecessaryLocalVariableJS: not inlined because the variable name improves readability
        const randomLetters = Array.from({ length: LENGTH_OF_RANDOM_STRING }, () => alphabet.charAt(Math.floor(Math.random() * alphabet.length))).join('');
        return randomLetters;
    }
}
