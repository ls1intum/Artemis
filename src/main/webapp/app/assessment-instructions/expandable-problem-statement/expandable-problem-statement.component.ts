import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

@Component({
    selector: 'jhi-expandable-problem-statement',
    templateUrl: './expandable-problem-statement.component.html',
    styleUrls: ['../assessment-instructions.scss'],
})
export class ExpandableProblemStatementComponent {
    @Input() exercise: ProgrammingExercise;
    isCollapsed = false;
    constructor() {}
}
