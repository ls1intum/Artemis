import { Component, Input } from '@angular/core';
import { BuildPlanCheckoutDirectoriesDTO } from 'app/entities/programming/build-plan-checkout-directories-dto';
import { AuxiliaryRepository } from 'app/entities/programming/programming-exercise-auxiliary-repository-model';

@Component({
    selector: 'jhi-programming-exercise-build-plan-checkout-directories',
    templateUrl: './programming-exercise-build-plan-checkout-directories.component.html',
    styleUrls: ['../../manage/programming-exercise-form.scss'],
    standalone: true,
})
export class ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent {
    @Input() checkoutDirectories?: BuildPlanCheckoutDirectoriesDTO;
    @Input() auxiliaryRepositories: AuxiliaryRepository[];

    ROOT_DIRECTORY_PATH = '/';
}
