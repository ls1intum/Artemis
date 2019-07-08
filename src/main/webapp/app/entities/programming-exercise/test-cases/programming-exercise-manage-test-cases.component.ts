import { Component, OnDestroy, OnInit, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { distinctUntilChanged } from 'rxjs/operators';
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

    testCases: ProgrammingExerciseTestCase[] = [];
    filteredTestCases: ProgrammingExerciseTestCase[] = [];

    showInactiveValue = false;

    get showInactive() {
        return this.showInactiveValue;
    }

    set showInactive(showInactive: boolean) {
        this.editing = null;
        this.showInactiveValue = showInactive;
        this.updateTestCaseFilter();
    }

    constructor(private testCaseService: ProgrammingExerciseTestCaseService, private route: ActivatedRoute) {}

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
                this.updateTestCaseFilter();
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
        this.testCaseService.updateWeight(this.exerciseId, editedTestCase.id, weight).subscribe((updatedTestCase: ProgrammingExerciseTestCase) => {
            this.editing = null;
            const updatedTestCases = this.testCases.map(testCase => (testCase.id === updatedTestCase.id ? updatedTestCase : testCase));
            this.testCaseService.notifyTestCases(this.exerciseId, updatedTestCases);
        });
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
        this.filteredTestCases = this.showInactiveValue ? this.testCases : this.testCases.filter(({ active }) => active);
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
