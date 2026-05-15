import { Component, input } from '@angular/core';
import { BuildPlanCheckoutDirectoriesDTO } from 'app/programming/shared/entities/build-plan-checkout-directories-dto';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';

@Component({
    selector: 'jhi-programming-exercise-build-plan-checkout-directories',
    templateUrl: './programming-exercise-build-plan-checkout-directories.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent {
    readonly checkoutDirectories = input<BuildPlanCheckoutDirectoriesDTO>();
    readonly auxiliaryRepositories = input<AuxiliaryRepository[]>(undefined!);

    ROOT_DIRECTORY_PATH = '/';
}
