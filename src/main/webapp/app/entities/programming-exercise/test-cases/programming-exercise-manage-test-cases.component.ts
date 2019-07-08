import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnInit, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { distinctUntilChanged } from 'rxjs/operators';
import { ProgrammingExerciseTestCaseService } from 'app/entities/programming-exercise/services';
import { IProgrammingExerciseTestCase, ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

@Component({
    selector: 'jhi-programming-exercise-manage-test-cases',
    templateUrl: './programming-exercise-manage-test-cases.component.html',
    styleUrls: ['./programming-exercise-manage-test-cases.scss'],
})
export class ProgrammingExerciseManageTestCasesComponent implements OnInit, OnDestroy {
    exerciseId: number;
    editing = null;
    testCases: ProgrammingExerciseTestCase[];
    testCaseSubscription: Subscription;
    paramSub: Subscription;

    constructor(private testCaseService: ProgrammingExerciseTestCaseService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.paramSub = this.route.params.pipe(distinctUntilChanged()).subscribe(params => {
            this.exerciseId = Number(params['exerciseId']);
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

    updateValue(event: any, cell: string, rowIndex: number) {
        const testCaseId = this.testCases[rowIndex].id;
        const weight = event.target.value;
        this.testCaseService.updateWeight(this.exerciseId, testCaseId, weight).subscribe(() => {
            this.editing = null;
            const updatedTestCases = this.testCases.map(testCase => (testCase.id === testCaseId ? { ...testCase, weight } : testCase));
            this.testCaseService.notifyTestCases(this.exerciseId, updatedTestCases);
        });
    }

    leaveEditing(event: any, cell: string, rowIndex: number) {
        this.editing[rowIndex + '-' + cell] = false;
    }
}
