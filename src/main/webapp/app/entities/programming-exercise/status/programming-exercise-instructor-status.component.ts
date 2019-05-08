import { Component, Input } from '@angular/core';
import { Result } from 'app/entities/result';
import { ParticipationType } from '../programming-exercise-participation.model';

@Component({
    selector: 'jhi-programming-exercise-instructor-status',
    templateUrl: './programming-exercise-instructor-status.component.html',
})
export class ProgrammingExerciseInstructorStatusComponent {
    @Input()
    participationType: ParticipationType;
    @Input()
    result: Result;
}
