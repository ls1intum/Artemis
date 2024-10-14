import { Component, input, output, viewChild } from '@angular/core';
import { NgModel } from '@angular/forms';

@Component({
    selector: 'jhi-programming-exercise-build-configuration',
    templateUrl: './programming-exercise-build-configuration.component.html',
    styleUrls: ['../../../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseBuildConfigurationComponent {
    dockerImage = input.required<string>();
    dockerImageChange = output<string>();

    timeout = input<number>();
    timeoutChange = output<number>();

    isAeolus = input.required<boolean>();

    dockerImageField = viewChild<NgModel>('dockerImageField');

    timeoutField = viewChild<NgModel>('timeoutField');
}
