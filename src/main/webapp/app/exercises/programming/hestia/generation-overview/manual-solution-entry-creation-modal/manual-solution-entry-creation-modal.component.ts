import { Component, EventEmitter, OnDestroy, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CodeHintService } from 'app/exercises/shared/exercise-hint/services/code-hint.service';
import { Subject } from 'rxjs';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { ProgrammingExerciseSolutionEntryService } from 'app/exercises/shared/exercise-hint/services/programming-exercise-solution-entry.service';

@Component({
    selector: 'jhi-manual-solution-entry-creation-modal',
    templateUrl: './manual-solution-entry-creation-modal.component.html',
})
export class ManualSolutionEntryCreationModalComponent implements OnInit, OnDestroy {
    solutionEntry = new ProgrammingExerciseSolutionEntry();

    exerciseId: number;
    codeHint?: CodeHint;
    onEntryCreated = new EventEmitter<ProgrammingExerciseSolutionEntry>();

    testCases?: ProgrammingExerciseTestCase[];
    solutionRepositoryFileNames?: string[];

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private activeModal: NgbActiveModal,
        private service: CodeHintService,
        private exerciseService: ProgrammingExerciseService,
        private solutionEntryService: ProgrammingExerciseSolutionEntryService,
    ) {
        this.solutionEntry.code = '';
        this.solutionEntry.line = 1;
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    ngOnInit(): void {
        this.solutionEntry.codeHint = this.codeHint;
        this.exerciseService.getAllTestCases(this.exerciseId).subscribe({
            next: (testCases) => {
                this.testCases = testCases;
            },
            error: (error) => this.dialogErrorSource.error(error.message),
        });
        this.exerciseService.getSolutionFileNames(this.exerciseId).subscribe({
            next: (fileNames) => {
                this.solutionRepositoryFileNames = fileNames;
            },
            error: (error) => this.dialogErrorSource.error(error.message),
        });
    }

    clear() {
        this.activeModal.close();
    }

    onCreateEntry() {
        this.solutionEntryService.createSolutionEntry(this.exerciseId, this.solutionEntry?.testCase?.id!, this.solutionEntry).subscribe({
            next: (createdEntry) => {
                this.dialogErrorSource.next('');
                this.onEntryCreated.emit(createdEntry);
                this.activeModal.close();
            },
            error: (error) => this.dialogErrorSource.error(error.message),
        });
    }
}
