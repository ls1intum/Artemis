import { Component, OnInit, Input } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { CodeHint } from 'app/entities/hestia/code-hint-model';

@Component({
    selector: 'jhi-code-hint-container',
    templateUrl: './code-hint-container.component.html',
})
export class CodeHintContainerComponent implements OnInit {
    @Input()
    codeHint: CodeHint;
    sortedSolutionEntries: ProgrammingExerciseSolutionEntry[];

    constructor(protected route: ActivatedRoute) {}

    ngOnInit() {
        this.setSortedSolutionEntriesForCodeHint();
    }

    setSortedSolutionEntriesForCodeHint() {
        this.sortedSolutionEntries =
            this.codeHint.solutionEntries?.sort((a, b) => {
                return a.filePath?.localeCompare(b.filePath!) || a.line! - b.line!;
            }) ?? [];
    }
}
