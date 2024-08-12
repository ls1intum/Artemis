import { AfterViewInit, Component, Input, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { NgModel } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisTableModule } from 'app/shared/table/table.module';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TableEditableFieldComponent } from 'app/shared/table/table-editable-field.component';
import { every } from 'lodash-es';
import { AddAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/add-auxiliary-repository-button.component';
import { RemoveAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/remove-auxiliary-repository-button.component';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-build-plan-checkout-directories.component';
import { ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-repository-and-build-plan-details.component';

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

    @ViewChild('shortName') shortNameField: NgModel;
    @ViewChildren(TableEditableFieldComponent) tableEditableFields?: QueryList<TableEditableFieldComponent>;
    @ViewChild('checkoutSolutionRepository') checkoutSolutionRepositoryField?: NgModel;

    ngAfterViewInit() {
        this.inputFieldSubscriptions.push(this.checkoutSolutionRepositoryField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.shortNameField.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.tableEditableFields?.changes.subscribe((fields: QueryList<TableEditableFieldComponent>) => {
            fields.toArray().forEach((field) => this.inputFieldSubscriptions.push(field.editingInput.valueChanges?.subscribe(() => this.calculateFormValid())));
        });
    }

    calculateFormValid() {
        this.formValid = Boolean(!this.shortNameField.invalid && this.isCheckoutSolutionRepositoryValid() && this.areAuxiliaryRepositoriesValid());
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
}
