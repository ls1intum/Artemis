import { AfterViewInit, Component, Input, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { NgModel } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisTableModule } from 'app/shared/table/table.module';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { ProgrammingExercise, ProjectType } from 'app/entities/programming-exercise.model';
import { TableEditableFieldComponent } from 'app/shared/table/table-editable-field.component';
import { every } from 'lodash-es';
import { AddAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/add-auxiliary-repository-button.component';
import { RemoveAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/remove-auxiliary-repository-button.component';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-build-plan-checkout-directories.component';
import { ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-repository-and-build-plan-details.component';
import { ImportOptions } from 'app/types/programming-exercises';

@Component({
    selector: 'jhi-programming-exercise-build-details',
    templateUrl: './programming-exercise-build-details.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
    standalone: true,
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisTableModule,
        AddAuxiliaryRepositoryButtonComponent,
        RemoveAuxiliaryRepositoryButtonComponent,
        NgxDatatableModule,
        ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent,
        ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent,
    ],
})
export class ProgrammingExerciseBuildDetailsComponent implements AfterViewInit {
    formValid: boolean;
    formValidChanges = new Subject<boolean>();

    inputFieldSubscriptions: (Subscription | undefined)[] = [];

    @Input() isLocal: boolean;
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    @Input() importOptions: ImportOptions;

    @ViewChild('shortName') shortNameField: NgModel;
    @ViewChildren(TableEditableFieldComponent) tableEditableFields?: QueryList<TableEditableFieldComponent>;
    @ViewChild('checkoutSolutionRepository') checkoutSolutionRepositoryField?: NgModel;
    @ViewChild('recreateBuildPlans') recreateBuildPlansField?: NgModel;
    @ViewChild('updateTemplateFiles') updateTemplateFilesField?: NgModel;

    ngAfterViewInit() {
        this.inputFieldSubscriptions.push(this.checkoutSolutionRepositoryField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.recreateBuildPlansField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.shortNameField.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.tableEditableFields?.changes.subscribe((fields: QueryList<TableEditableFieldComponent>) => {
            fields.toArray().forEach((field) => this.inputFieldSubscriptions.push(field.editingInput.valueChanges?.subscribe(() => this.calculateFormValid())));
        });
        this.inputFieldSubscriptions.push(this.updateTemplateFilesField?.valueChanges?.subscribe(() => this.calculateFormValid()));
    }

    calculateFormValid() {
        this.formValid = Boolean(
            !this.shortNameField.invalid &&
                this.isCheckoutSolutionRepositoryValid() &&
                this.areAuxiliaryRepositoriesValid() &&
                this.isRecreateBuildPlansValid() &&
                this.isUpdateTemplateFilesValid(),
        );
        this.formValidChanges.next(this.formValid);
    }

    private areAuxiliaryRepositoriesValid(): boolean {
        return (
            (every(
                this.tableEditableFields?.map((field) => field.editingInput.valid),
                Boolean,
            ) &&
                !this.programmingExerciseCreationConfig.auxiliaryRepositoryDuplicateDirectories &&
                !this.programmingExerciseCreationConfig.auxiliaryRepositoryDuplicateNames) ||
            !this.programmingExercise.auxiliaryRepositories?.length
        );
    }

    private isCheckoutSolutionRepositoryValid(): boolean {
        return Boolean(
            this.checkoutSolutionRepositoryField?.valid ||
                this.programmingExercise.id ||
                !this.programmingExercise.programmingLanguage ||
                !this.programmingExerciseCreationConfig.checkoutSolutionRepositoryAllowed,
        );
    }

    private isUpdateTemplateFilesValid(): boolean {
        return (
            this.updateTemplateFilesField?.valid ||
            !this.programmingExerciseCreationConfig.isImportFromExistingExercise ||
            this.programmingExercise.projectType === ProjectType.PLAIN_GRADLE ||
            this.programmingExercise.projectType === ProjectType.GRADLE_GRADLE
        );
    }

    private isRecreateBuildPlansValid(): boolean {
        return this.recreateBuildPlansField?.valid || !this.programmingExerciseCreationConfig.isImportFromExistingExercise;
    }

    protected readonly ProjectType = ProjectType;
}
