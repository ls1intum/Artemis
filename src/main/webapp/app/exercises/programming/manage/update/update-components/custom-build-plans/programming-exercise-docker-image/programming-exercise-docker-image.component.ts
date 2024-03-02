import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { NgModel } from '@angular/forms';

@Component({
    selector: 'jhi-programming-exercise-docker-image',
    templateUrl: './programming-exercise-docker-image.component.html',
    styleUrls: ['../../../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseDockerImageComponent {
    @Input() dockerImage: string;
    @Output() dockerImageChange = new EventEmitter<string>();

    @ViewChild('dockerImageField') dockerImageField?: NgModel;
}
