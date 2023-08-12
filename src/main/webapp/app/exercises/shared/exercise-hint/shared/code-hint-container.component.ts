import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';

import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { CodeHintService } from 'app/exercises/shared/exercise-hint/services/code-hint.service';
import { faTimes } from '@fortawesome/free-solid-svg-icons';

/**
 * Component containing the solution entries for a {@link CodeHint}.
 * The entries are sorted by name (primary) and start line number (secondary)
 */
@Component({
    selector: 'jhi-code-hint-container',
    templateUrl: './code-hint-container.component.html',
})
export class CodeHintContainerComponent implements OnInit, OnDestroy {
    @Input()
    codeHint: CodeHint;

    @Input()
    enableEditing = false;

    sortedSolutionEntries: ProgrammingExerciseSolutionEntry[];

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    faTimes = faTimes;

    constructor(protected route: ActivatedRoute, private codeHintService: CodeHintService) {}

    ngOnInit() {
        this.setSortedSolutionEntriesForCodeHint();
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    setSortedSolutionEntriesForCodeHint() {
        this.sortedSolutionEntries =
            this.codeHint.solutionEntries?.sort((a, b) => {
                return a.filePath?.localeCompare(b.filePath!) || a.line! - b.line!;
            }) ?? [];
    }

    /**
     * Removes a solution entry from the code hint
     * @param solutionEntryId of the solution entry to be removed
     */
    removeEntryFromCodeHint(solutionEntryId: number) {
        this.codeHintService.removeSolutionEntryFromCodeHint(this.codeHint.exercise!.id!, this.codeHint.id!, solutionEntryId).subscribe({
            next: () => {
                this.sortedSolutionEntries = this.sortedSolutionEntries.filter((entry) => entry.id !== solutionEntryId);
                this.codeHint.solutionEntries = this.sortedSolutionEntries;
                this.dialogErrorSource.next('');
            },
            error: (error) => {
                this.dialogErrorSource.next(error.message);
            },
        });
    }
}
