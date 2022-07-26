import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { SolutionEntryDetailsModalComponent } from 'app/exercises/programming/hestia/generation-overview/solution-entry-details-modal/solution-entry-details-modal.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseTestCaseType } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AlertService } from 'app/core/util/alert.service';
import { Subject } from 'rxjs';
import { faSort, faSortDown, faSortUp, faTimes } from '@fortawesome/free-solid-svg-icons';
import { CodeHintService } from 'app/exercises/shared/exercise-hint/services/code-hint.service';
import { ManualSolutionEntryCreationModalComponent } from 'app/exercises/programming/hestia/generation-overview/manual-solution-entry-creation-modal/manual-solution-entry-creation-modal.component';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { ProgrammingExerciseSolutionEntryService } from 'app/exercises/shared/exercise-hint/services/programming-exercise-solution-entry.service';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-solution-entry-generation-step',
    templateUrl: './solution-entry-generation-step.component.html',
    styleUrls: ['../../code-hint-generation-overview/code-hint-generation-overview.component.scss'],
})
export class SolutionEntryGenerationStepComponent implements OnInit, OnDestroy {
    @Input()
    exercise: ProgrammingExercise;

    @Output()
    onEntryUpdate = new EventEmitter<ProgrammingExerciseSolutionEntry[]>();

    isLoading: boolean;
    solutionEntries: ProgrammingExerciseSolutionEntry[];
    faTimes = faTimes;

    testCaseSortOrder?: SortingOrder;
    faSort = faSort;
    faSortUp = faSortUp;
    faSortDown = faSortDown;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    readonly SortingOrder = SortingOrder;

    constructor(
        private modalService: NgbModal,
        private exerciseService: ProgrammingExerciseService,
        private alertService: AlertService,
        private artemisTranslatePipe: ArtemisTranslatePipe,
        private codeHintService: CodeHintService,
        private solutionEntryService: ProgrammingExerciseSolutionEntryService,
    ) {}

    ngOnInit() {
        this.isLoading = true;
        this.solutionEntryService.getSolutionEntriesForExercise(this.exercise.id!).subscribe({
            next: (solutionEntries: ProgrammingExerciseSolutionEntry[]) => {
                this.solutionEntries = solutionEntries;
                this.isLoading = false;
                this.onEntryUpdate.emit(this.solutionEntries);
            },
            error: (error) => {
                this.isLoading = false;
                this.alertService.error(error.message);
            },
        });
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    openSolutionEntryModal(solutionEntry: ProgrammingExerciseSolutionEntry, isEditable: boolean) {
        const modalRef: NgbModalRef = this.modalService.open(SolutionEntryDetailsModalComponent as Component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseId = this.exercise.id;
        modalRef.componentInstance.solutionEntry = solutionEntry;
        modalRef.componentInstance.isEditable = isEditable;
    }

    openManualEntryCreationModal() {
        const modalRef: NgbModalRef = this.modalService.open(ManualSolutionEntryCreationModalComponent as Component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseId = this.exercise.id;
        modalRef.componentInstance.onEntryCreated.subscribe((createdEntry: ProgrammingExerciseSolutionEntry) => {
            this.solutionEntries.push(createdEntry);
            this.onEntryUpdate.emit(this.solutionEntries);
        });
    }

    onGenerateStructuralSolutionEntries() {
        this.exerciseService.createStructuralSolutionEntries(this.exercise.id!).subscribe({
            next: (updatedStructuralEntries) => {
                this.alertService.success('artemisApp.programmingExercise.createStructuralSolutionEntriesSuccess');
                // replace all structural entries
                const result = this.removeSolutionEntriesOfTypeFromArray(this.solutionEntries, ProgrammingExerciseTestCaseType.STRUCTURAL);
                Array.prototype.push.apply(result, updatedStructuralEntries);
                this.solutionEntries = result;
                this.testCaseSortOrder = undefined;
                this.onEntryUpdate.emit(this.solutionEntries);
            },
            error: (error) => this.alertService.error(error.message),
        });
    }

    onGenerateBehavioralSolutionEntries() {
        this.exerciseService.createBehavioralSolutionEntries(this.exercise.id!).subscribe({
            next: (updatedBehavioralEntries) => {
                this.alertService.success('artemisApp.programmingExercise.createBehavioralSolutionEntriesSuccess');
                // replace all behavioral entries
                const result = this.removeSolutionEntriesOfTypeFromArray(this.solutionEntries, ProgrammingExerciseTestCaseType.BEHAVIORAL);
                Array.prototype.push.apply(result, updatedBehavioralEntries);
                this.solutionEntries = result;
                this.testCaseSortOrder = undefined;
                this.onEntryUpdate.emit(this.solutionEntries);
            },
            error: (error) => this.alertService.error(error.message),
        });
    }

    openBulkDeletionModal() {
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.title = 'artemisApp.codeHint.management.step3.deleteAllEntriesButton.title';
        modalRef.componentInstance.text = this.artemisTranslatePipe.transform('artemisApp.codeHint.management.step3.deleteAllEntriesButton.question');
        modalRef.result.then(() => {
            this.deleteAllSolutionEntries();
        });
    }

    deleteAllSolutionEntries() {
        this.solutionEntryService.deleteAllSolutionEntriesForExercise(this.exercise?.id!).subscribe({
            next: () => {
                this.solutionEntries = [];
                this.onEntryUpdate.emit([]);
                this.alertService.success('artemisApp.codeHint.management.step3.deleteAllEntriesButton.success');
                this.dialogErrorSource.next('');
            },
            error: (error) => this.dialogErrorSource.error(error.message),
        });
    }

    deleteSolutionEntry(entry: ProgrammingExerciseSolutionEntry) {
        this.solutionEntryService.deleteSolutionEntry(this.exercise?.id!, entry.testCase?.id!, entry.id!).subscribe({
            next: () => {
                this.solutionEntries = this.solutionEntries.filter((existingEntry) => entry !== existingEntry);
                this.dialogErrorSource.next('');
                this.onEntryUpdate.emit(this.solutionEntries);
            },
            error: (error) => this.dialogErrorSource.error(error.message),
        });
    }

    changeTestCaseSortOrder() {
        switch (this.testCaseSortOrder) {
            case SortingOrder.ASCENDING:
                this.solutionEntries = this.solutionEntries.reverse();
                this.testCaseSortOrder = SortingOrder.DESCENDING;
                break;
            case SortingOrder.DESCENDING:
                this.solutionEntries = this.solutionEntries.reverse();
                this.testCaseSortOrder = SortingOrder.ASCENDING;
                break;
            case undefined:
                this.solutionEntries = this.solutionEntries.sort((a, b) => a.testCase?.testName!.localeCompare(b.testCase?.testName!)!);
                this.testCaseSortOrder = SortingOrder.ASCENDING;
        }
    }

    private removeSolutionEntriesOfTypeFromArray(entries: ProgrammingExerciseSolutionEntry[], typeToRemove: ProgrammingExerciseTestCaseType) {
        return entries.filter((entry) => entry.testCase?.type !== typeToRemove);
    }
}
