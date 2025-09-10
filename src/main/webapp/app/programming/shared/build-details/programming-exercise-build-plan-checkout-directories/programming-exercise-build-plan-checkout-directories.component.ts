import { Component, Input } from '@angular/core';
import { BuildPlanCheckoutDirectoriesDTO } from 'app/programming/shared/entities/build-plan-checkout-directories-dto';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';

@Component({
    selector: 'jhi-programming-exercise-build-plan-checkout-directories',
    templateUrl: './programming-exercise-build-plan-checkout-directories.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent {
    @Input() checkoutDirectories?: BuildPlanCheckoutDirectoriesDTO;
    @Input() auxiliaryRepositories: AuxiliaryRepository[];

    ROOT_DIRECTORY_PATH = '/';
}
