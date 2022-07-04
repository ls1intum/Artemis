import { Component, OnDestroy } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { Subject } from 'rxjs';
import { ProgrammingExerciseSolutionEntryService } from 'app/exercises/shared/exercise-hint/services/programming-exercise-solution-entry.service';

@Component({
    selector: 'jhi-solution-entry-details-modal',
    templateUrl: './solution-entry-details-modal.component.html',
})
export class SolutionEntryDetailsModalComponent implements OnDestroy {
    exerciseId: number;
    solutionEntry: ProgrammingExerciseSolutionEntry;
    isEditable: boolean;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(private activeModal: NgbActiveModal, private solutionEntryService: ProgrammingExerciseSolutionEntryService) {}

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    clear() {
        this.activeModal.close();
    }

    saveSolutionEntry() {
        this.solutionEntryService.updateSolutionEntry(this.exerciseId, this.solutionEntry.testCase?.id!, this.solutionEntry.id!, this.solutionEntry).subscribe({
            next: (updatedEntry) => {
                this.solutionEntry = updatedEntry;
                this.activeModal.close();
            },
            error: (error) => this.dialogErrorSource.error(error.message),
        });
    }
}
