import { ApplicationRef, Component, ComponentFactoryResolver, Injector, Input } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Result } from 'app/entities/result';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { ExerciseHintStudentDialogComponent } from 'app/entities/exercise-hint';
import { TestCaseState } from 'app/entities/programming-exercise/instructions/instructions-render/service/programming-exercise-instruction.service';
import { ProgrammingExerciseInstructionService } from 'app/entities/programming-exercise/instructions/instructions-render/service/programming-exercise-instruction.service';
import { ProgrammingExerciseInstructionResultDetailComponent } from 'app/entities/programming-exercise/instructions/instructions-render/task/programming-exercise-instructions-result-detail.component';

@Component({
    selector: 'jhi-programming-exercise-instructions-task-status',
    templateUrl: './programming-exercise-instruction-task-status.component.html',
    styleUrls: ['./programming-exercise-instruction-task-status.scss'],
})
export class ProgrammingExerciseInstructionTaskStatusComponent {
    TestCaseState = TestCaseState;
    translationBasePath = 'artemisApp.editor.testStatusLabels.';

    @Input() taskName: string;
    @Input()
    get tests() {
        return this.testsValue;
    }
    @Input() exerciseHints: ExerciseHint[] = [];
    @Input() latestResult: Result | null;

    ngbModalRef: NgbModalRef | null;

    testsValue: string[];
    testCaseState: TestCaseState;

    successfulTests: string[];
    notExecutedTests: string[];
    failedTests: string[];

    constructor(
        private programmingExerciseInstructionService: ProgrammingExerciseInstructionService,
        private componentFactoryResolver: ComponentFactoryResolver,
        private appRef: ApplicationRef,
        private injector: Injector,
        private modalService: NgbModal,
    ) {}

    set tests(tests: string[]) {
        this.testsValue = tests;
        const {
            testCaseState,
            detailed: { successfulTests, notExecutedTests, failedTests },
        } = this.programmingExerciseInstructionService.testStatusForTask(this.tests, this.latestResult);
        this.testCaseState = testCaseState;
        this.successfulTests = successfulTests;
        this.notExecutedTests = notExecutedTests;
        this.failedTests = failedTests;
    }

    /**
     * @function showDetailsForTests
     * @desc Opens the ResultDetailComponent as popup; displays test results
     * @param result {Result} Result object, mostly latestResult
     * @param tests {string} Identifies the testcase
     */
    public showDetailsForTests() {
        if (!this.latestResult) {
            return;
        }
        const modalRef = this.modalService.open(ProgrammingExerciseInstructionResultDetailComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.result = this.latestResult;
        modalRef.componentInstance.tests = this.tests;
    }

    public openHintsModal() {
        // Open hint modal.
        this.ngbModalRef = this.modalService.open(ExerciseHintStudentDialogComponent as Component, { size: 'lg', backdrop: 'static' });
        this.ngbModalRef.componentInstance.exerciseHints = this.exerciseHints;
        this.ngbModalRef.result.then(
            () => {
                this.ngbModalRef = null;
            },
            () => {
                this.ngbModalRef = null;
            },
        );
    }
}
