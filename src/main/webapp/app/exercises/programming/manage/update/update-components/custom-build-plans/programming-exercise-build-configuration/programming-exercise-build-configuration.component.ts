import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { NgModel } from '@angular/forms';
import { PROGRAMMING_EXERCISE_CHECKOUT_PATH_PATTERN } from 'app/shared/constants/input.constants';

@Component({
    selector: 'jhi-programming-exercise-build-configuration',
    templateUrl: './programming-exercise-build-configuration.component.html',
    styleUrls: ['../../../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseBuildConfigurationComponent {
    @Input() dockerImage: string;
    @Output() dockerImageChange = new EventEmitter<string>();

    @Input() checkoutPath: string;
    @Output() checkoutPathChange = new EventEmitter<string>();

    @Input() timeout: number;
    @Output() timeoutChange = new EventEmitter<number>();

    @ViewChild('dockerImageField') dockerImageField?: NgModel;

    @ViewChild('checkoutPathField') checkoutPathField?: NgModel;

    @ViewChild('timeoutField') timeoutField?: NgModel;

    checkoutPathPattern: string = PROGRAMMING_EXERCISE_CHECKOUT_PATH_PATTERN;
}
