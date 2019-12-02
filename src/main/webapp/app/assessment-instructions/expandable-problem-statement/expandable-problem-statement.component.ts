import { Component, OnInit, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { Exercise, ExerciseType } from 'app/entities/exercise';

@Component({
    selector: 'jhi-expandable-problem-statement',
    templateUrl: './expandable-problem-statement.component.html',
    styleUrls: ['../assessment-instructions.scss'],
})
export class ExpandableProblemStatementComponent {
    @Input() exercise: ProgrammingExercise;
    @Input() isCollapsed = false;

    readonly ExerciseType_PROGRAMMING = ExerciseType.PROGRAMMING;
    constructor() {}
}
