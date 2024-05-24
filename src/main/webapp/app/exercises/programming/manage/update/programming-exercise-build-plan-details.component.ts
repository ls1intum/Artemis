import { Component, Input } from '@angular/core';
import { BuildPlanCheckoutDirectoriesDTO } from 'app/entities/build-plan-checkout-directories-dto';

@Component({
    selector: 'jhi-programming-exercise-build-plan-details',
    templateUrl: './programming-exercise-build-plan-details.component.html',
    styleUrls: ['../programming-exercise-form.scss'],
})
export class ProgrammingExerciseBuildPlanDetailsComponent {
    @Input() checkoutDirectories?: BuildPlanCheckoutDirectoriesDTO;
    @Input() auxiliaryRepositoryCheckoutDirectories: string[];
}
