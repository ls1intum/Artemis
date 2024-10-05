import { Component, OnDestroy, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { Subject } from 'rxjs';
import { ProgrammingExerciseSolutionEntryService } from 'app/exercises/shared/exercise-hint/services/programming-exercise-solution-entry.service';

@Component({
    selector: 'jhi-solution-entry-details-modal',
    templateUrl: './solution-entry-details-modal.component.html',
})
export class SolutionEntryDetailsModalComponent implements OnDestroy {
    private activeModal = inject(NgbActiveModal);
    private solutionEntryService = inject(ProgrammingExerciseSolutionEntryService);

    exerciseId: number;
    solutionEntry: ProgrammingExerciseSolutionEntry;
    isEditable: boolean;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    clear() {
        this.activeModal.close();
    }

    saveSolutionEntry() {
        // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
        this.solutionEntryService.updateSolutionEntry(this.exerciseId, this.solutionEntry.testCase?.id!, this.solutionEntry.id!, this.solutionEntry).subscribe({
            next: (updatedEntry) => {
                this.solutionEntry = updatedEntry;
                this.activeModal.close();
            },
            error: (error) => this.dialogErrorSource.error(error.message),
        });
    }
}
