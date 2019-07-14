import { Component, OnDestroy, OnInit, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { distinctUntilChanged } from 'rxjs/operators';
import { differenceWith as _differenceWith, intersectionBy as _intersectionBy, differenceBy as _differenceBy, unionBy as _unionBy } from 'lodash';
import { JhiAlertService } from 'ng-jhipster';
import { ProgrammingExerciseTestCaseService } from 'app/entities/programming-exercise/services';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';

@Component({
    selector: 'jhi-programming-exercise-manage-test-cases',
    templateUrl: './programming-exercise-manage-test-cases.component.html',
    styleUrls: ['./programming-exercise-manage-test-cases.scss'],
})
export class ProgrammingExerciseManageTestCasesComponent implements OnInit, OnDestroy {
    @ViewChild('editingInput', { static: false }) editingInput: ElementRef;
    exerciseId: number;
    editing: ProgrammingExerciseTestCase | null = null;
    testCaseSubscription: Subscription;
    paramSub: Subscription;

    testCasesValue: ProgrammingExerciseTestCase[] = [];
    changedTestCaseIds: number[] = [];
    filteredTestCases: ProgrammingExerciseTestCase[] = [];

    showInactiveValue = false;

    isSaving = false;

    get testCases() {
        return this.testCasesValue;
    }

    set testCases(testCases: ProgrammingExerciseTestCase[]) {
        this.testCasesValue = testCases;
        this.updateTestCaseFilter();
    }

    get showInactive() {
        return this.showInactiveValue;
    }

    set showInactive(showInactive: boolean) {
        this.editing = null;
        this.showInactiveValue = showInactive;
        this.updateTestCaseFilter();
    }

    constructor(private testCaseService: ProgrammingExerciseTestCaseService, private route: ActivatedRoute, private alertService: JhiAlertService) {}

    /**
     * Subscribes to the route params to get the current exerciseId.
     * Uses the exerciseId to subscribe to the newest value of the exercise's test cases.
     */
    ngOnInit(): void {
        this.paramSub = this.route.params.pipe(distinctUntilChanged()).subscribe(params => {
            this.exerciseId = Number(params['exerciseId']);
            this.editing = null;
            if (this.testCaseSubscription) {
                this.testCaseSubscription.unsubscribe();
            }
            this.testCaseSubscription = this.testCaseService.subscribeForTestCases(this.exerciseId).subscribe((testCases: ProgrammingExerciseTestCase[]) => {
                this.testCases = testCases;
            });
        });
    }

    ngOnDestroy(): void {
        if (this.testCaseSubscription) {
            this.testCaseSubscription.unsubscribe();
        }
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    /**
     * Show an input to edit the test cases weight.
     * @param rowIndex
     */
    enterEditing(rowIndex: number) {
        this.editing = this.filteredTestCases[rowIndex];
        setTimeout(() => {
            if (this.editingInput) {
                this.editingInput.nativeElement.focus();
            }
        });
    }

    /**
     * Hide input.
     */
    leaveEditingWithoutSaving() {
        this.editing = null;
    }

    /**
     * Update the weight of the edited test case.
     * @param event
     */
    updateWeight(event: any) {
        if (!this.editing) {
            return;
        }
        const editedTestCase = this.editing;
        const weight = event.target.value;
        // If the weight has not changed, don't do anything besides closing the input.
        if (weight === editedTestCase.weight) {
            this.editing = null;
            return;
        }
        this.changedTestCaseIds = this.changedTestCaseIds.includes(editedTestCase.id) ? this.changedTestCaseIds : [...this.changedTestCaseIds, editedTestCase.id];
        this.testCases = this.testCases.map(testCase => (testCase.id !== editedTestCase.id ? testCase : { ...testCase, weight: event.target.value }));
        this.editing = null;
    }

    saveWeights() {
        this.isSaving = true;

        const testCasesToUpdate = _differenceWith(this.testCases, this.changedTestCaseIds, (testCase: ProgrammingExerciseTestCase, id: number) => testCase.id === id);
        const weightUpdates = testCasesToUpdate.map(({ id, weight }) => ({ id, weight }));

        this.testCaseService.updateWeights(this.exerciseId, weightUpdates).subscribe(
            (updatedTestCases: ProgrammingExerciseTestCase[]) => {
                this.editing = null;
                this.isSaving = false;
                // From successfully updated test cases from dirty checking list.
                this.changedTestCaseIds = _differenceWith(
                    Array.from(this.changedTestCaseIds),
                    updatedTestCases,
                    (id: number, testCase: ProgrammingExerciseTestCase) => testCase.id === id,
                );

                // Generate the new list of test cases with the updated weights and notify the test case service.
                const newTestCases = _unionBy(updatedTestCases, this.testCases, 'id');
                this.testCaseService.notifyTestCases(this.exerciseId, newTestCases);

                // Find out if there are test cases that were not updated, show an error.
                const notUpdatedTestCases = _differenceBy(testCasesToUpdate, updatedTestCases, 'id');
                if (notUpdatedTestCases.length) {
                    this.alertService.error(`artemisApp.programmingExercise.manageTestCases.weightCouldNotBeUpdated`, { testCases: notUpdatedTestCases });
                }
            },
            (err: HttpErrorResponse) => {
                this.editing = null;
                this.isSaving = false;
                this.alertService.error(`artemisApp.programmingExercise.manageTestCases.weightCouldNotBeUpdated`, { testCases: testCasesToUpdate });
            },
        );
    }

    /**
     * Sets the weights of all test cases to 1.
     */
    resetWeights() {
        this.editing = null;
        this.testCaseService.resetWeights(this.exerciseId).subscribe((testCases: ProgrammingExerciseTestCase[]) => {
            this.testCaseService.notifyTestCases(this.exerciseId, testCases);
        });
    }

    /**
     * Executes filtering on all availabile test cases with the specified params.
     */
    updateTestCaseFilter = () => {
        this.filteredTestCases = !this.showInactiveValue && this.testCases ? this.testCases.filter(({ active }) => active) : this.testCases;
    };

    /**
     * Makes inactive test cases grey.
     *
     * @param row
     */
    getRowClass(row: ProgrammingExerciseTestCase) {
        return !row.active ? 'test-case--inactive' : '';
    }
}
