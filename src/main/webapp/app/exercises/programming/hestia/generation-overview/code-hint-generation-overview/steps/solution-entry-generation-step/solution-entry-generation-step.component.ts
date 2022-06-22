import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { SolutionEntryDetailsModalComponent } from 'app/exercises/programming/hestia/generation-overview/code-hint-generation-overview/solution-entry-details-modal/solution-entry-details-modal.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseTestCaseType } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AlertService, AlertType } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-solution-entry-generation-step',
    templateUrl: './solution-entry-generation-step.component.html',
    styleUrls: ['../../code-hint-generation-overview.component.scss'],
})
export class SolutionEntryGenerationStepComponent implements OnInit {
    @Input()
    exercise: ProgrammingExercise;

    @Output()
    onSelectionChanges = new EventEmitter<ProgrammingExerciseSolutionEntry[]>();

    isLoading: boolean;

    solutionEntries: Map<ProgrammingExerciseSolutionEntry, boolean>;
    allEntriesSelected = true;

    constructor(private modalService: NgbModal, private exerciseService: ProgrammingExerciseService, private alertService: AlertService) {}

    ngOnInit() {
        this.exerciseService.getSolutionEntriesForExercise(this.exercise.id!).subscribe({
            next: (response: ProgrammingExerciseSolutionEntry[]) => {
                const selectedAllSolutionEntries = new Map<ProgrammingExerciseSolutionEntry, boolean>();
                response.forEach((entry) => selectedAllSolutionEntries.set(entry, true));
                this.solutionEntries = selectedAllSolutionEntries;
                this.isLoading = false;
                this.allEntriesSelected = true;
                this.onSelectionChanges.emit(this.getSelectedEntries());
            },
            error: () => {},
        });
    }

    onSolutionEntryView(solutionEntry: ProgrammingExerciseSolutionEntry, isEditable: boolean) {
        const modalRef: NgbModalRef = this.modalService.open(SolutionEntryDetailsModalComponent as Component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.solutionEntry = solutionEntry;
        modalRef.componentInstance.isEditable = isEditable;
    }

    selectSingleEntry(entry: ProgrammingExerciseSolutionEntry, isSelected: boolean) {
        this.solutionEntries?.set(entry, isSelected);
        this.allEntriesSelected = Array.from(this.solutionEntries!.values()).every((selected) => selected);
        this.onSelectionChanges.emit(this.getSelectedEntries());
    }

    updateBulkEntrySelection() {
        if (!this.allEntriesSelected) {
            this.solutionEntries?.forEach((value, key) => this.solutionEntries?.set(key, true));
            this.allEntriesSelected = true;
        } else {
            this.solutionEntries?.forEach((value, key) => this.solutionEntries?.set(key, false));
            this.allEntriesSelected = false;
        }
        this.onSelectionChanges.emit(this.getSelectedEntries());
    }

    getSelectedEntries(): ProgrammingExerciseSolutionEntry[] {
        const result: ProgrammingExerciseSolutionEntry[] = [];
        this.solutionEntries?.forEach((selected, entry) => {
            if (selected) {
                result.push(entry);
            }
        });
        return result;
    }

    onGenerateStructuralSolutionEntries() {
        this.exerciseService.createStructuralSolutionEntries(this.exercise.id!).subscribe({
            next: (updatedStructuralEntries) => {
                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: 'artemisApp.programmingExercise.createStructuralSolutionEntriesSuccess',
                });
                const updatedSolutionEntries = new Map<ProgrammingExerciseSolutionEntry, boolean>();
                this.solutionEntries?.forEach((selected, entry) => {
                    if (entry.testCase?.type === ProgrammingExerciseTestCaseType.BEHAVIORAL) {
                        updatedSolutionEntries.set(entry, selected);
                    }
                });
                updatedStructuralEntries.forEach((entry) => updatedSolutionEntries.set(entry, true));
                this.solutionEntries = updatedSolutionEntries;
                this.allEntriesSelected = true;
            },
            error: () => {},
        });
    }

    onGenerateBehavioralSolutionEntries() {
        this.exerciseService.createBehavioralSolutionEntries(this.exercise.id!).subscribe({
            next: (updatedBehavioralEntries) => {
                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: 'artemisApp.programmingExercise.createBehavioralSolutionEntriesSuccess',
                });
                const updatedSolutionEntries = new Map<ProgrammingExerciseSolutionEntry, boolean>();
                this.solutionEntries?.forEach((selected, entry) => {
                    if (entry.testCase?.type && entry.testCase?.type !== ProgrammingExerciseTestCaseType.BEHAVIORAL) {
                        updatedSolutionEntries.set(entry, selected);
                    }
                });
                updatedBehavioralEntries.forEach((entry) => updatedSolutionEntries.set(entry, true));
                this.solutionEntries = updatedSolutionEntries;
                this.allEntriesSelected = true;
            },
            error: () => {},
        });
    }
}
