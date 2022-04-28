import { Component, OnInit, OnDestroy, Input } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';

import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { ProgrammingExerciseSolutionEntryService } from 'app/exercises/shared/exercise-hint/shared/programming-exercise-solution-entry.service';

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

    constructor(protected route: ActivatedRoute, private solutionEntryService: ProgrammingExerciseSolutionEntryService) {}

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
     * Delete a solution entry by its id
     * @param solutionEntryId of the solution entry to be deleted
     */
    deleteEntry(solutionEntryId: number) {
        this.solutionEntryService.deleteSolutionEntry(this.codeHint.exercise!.id!, this.codeHint.id!, solutionEntryId).subscribe({
            next: () => {
                this.sortedSolutionEntries = this.sortedSolutionEntries.filter((entry) => entry.id !== solutionEntryId);
                this.dialogErrorSource.next('');
            },
            error: (error) => {
                this.dialogErrorSource.next(error.message);
            },
        });
    }
}
