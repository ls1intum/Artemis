import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-info',
    templateUrl: './programming-exercise-update-wizard-information.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardInformationComponent {
    @Input() isImport: boolean;

    @Input() titleNamePattern: string;
    @Input() programmingExercise: ProgrammingExercise;
    @Input() shortNamePattern: RegExp;

    @Input() invalidRepositoryNamePattern: RegExp;
    @Input() invalidDirectoryNamePattern: RegExp;
    @Input() updateRepositoryName: (auxiliaryRepository: AuxiliaryRepository) => (newValue: any) => string | undefined;
    @Input() updateCheckoutDirectory: (editedAuxiliaryRepository: AuxiliaryRepository) => (newValue: any) => string | undefined;
    @Input() refreshAuxiliaryRepositoryChecks: () => void;
    @Input() auxiliaryRepositoryDuplicateNames: boolean;
    @Input() auxiliaryRepositoryDuplicateDirectories: boolean;

    @Input() exerciseCategories: ExerciseCategory[];
    @Input() existingCategories: ExerciseCategory[];
    @Input() updateCategories: (categories: ExerciseCategory[]) => void;
}
