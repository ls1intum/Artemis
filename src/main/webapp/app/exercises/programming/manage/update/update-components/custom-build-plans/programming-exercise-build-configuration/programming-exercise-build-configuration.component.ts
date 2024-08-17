import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { NgModel } from '@angular/forms';

@Component({
    selector: 'jhi-programming-exercise-build-configuration',
    templateUrl: './programming-exercise-build-configuration.component.html',
    styleUrls: ['../../../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseBuildConfigurationComponent {
    @Input() dockerImage: string;
    @Output() dockerImageChange = new EventEmitter<string>();

    @Input() timeout: number;
    @Output() timeoutChange = new EventEmitter<number>();

    @Input() isAeolus: boolean = false;

    @ViewChild('dockerImageField') dockerImageField?: NgModel;

    @ViewChild('timeoutField') timeoutField?: NgModel;
}
