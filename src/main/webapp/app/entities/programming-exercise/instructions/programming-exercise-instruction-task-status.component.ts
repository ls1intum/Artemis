import { ApplicationRef, Component, ComponentFactoryResolver, Injector, Input, SimpleChanges, OnChanges } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { EditorInstructionsResultDetailComponent } from 'app/code-editor/instructions/code-editor-instructions-result-detail';
import { Result } from 'app/entities/result';
import { TestCaseState } from 'app/entities/programming-exercise';
import { isLegacyResult } from 'app/entities/programming-exercise/utils/programming-exercise.utils';
import { ProgrammingExerciseInstructionService } from 'app/entities/programming-exercise/instructions/programming-exercise-instruction.service';

@Component({
    selector: 'jhi-programming-exercise-instructions-task-status',
    template: `
        <div>
            <fa-icon *ngIf="testCaseState === TestCaseState.SUCCESS" [icon]="['far', 'check-circle']" size="lg" class="test-icon text-success"></fa-icon>
            <fa-icon *ngIf="testCaseState === TestCaseState.FAIL" [icon]="['far', 'times-circle']" size="lg" class="test-icon text-danger"></fa-icon>
            <fa-icon
                *ngIf="testCaseState === TestCaseState.NO_RESULT || testCaseState === TestCaseState.NOT_EXECUTED"
                [icon]="['far', 'question-circle']"
                size="lg"
                class="test-icon text-secondary"
            ></fa-icon>
            <span *ngIf="taskName" class="task-name">{{ taskName }}</span>
            <span>
                <span
                    *ngIf="testCaseState === TestCaseState.SUCCESS || testCaseState === TestCaseState.NO_RESULT || !tests.length"
                    [class.text-success]="testCaseState === TestCaseState.SUCCESS"
                    [class.text-secondary]="testCaseState === TestCaseState.NO_RESULT"
                    >{{ testResultLabel }}</span
                >
                <span
                    *ngIf="testCaseState === TestCaseState.FAIL || testCaseState === TestCaseState.NOT_EXECUTED"
                    class="test-status--linked"
                    [class.danger]="testCaseState === TestCaseState.FAIL"
                    [class.text-secondary]="testCaseState === TestCaseState.NOT_EXECUTED"
                    (click)="showDetailsForTests()"
                    >{{ testResultLabel }}</span
                >
            </span>
        </div>
    `,
    styles: ['.test-status--linked {text-decoration: underline; cursor: pointer}', '.test-icon, .task-name {font-weight: bold}'],
})
export class ProgrammingExerciseInstructionTaskStatusComponent {
    TestCaseState = TestCaseState;

    @Input() taskName: string;
    @Input()
    get tests() {
        return this.testsValue;
    }
    @Input() latestResult: Result | null;

    testsValue: string[];
    testCaseState: TestCaseState;
    testResultLabel: string;

    constructor(
        private programmingExerciseInstructionService: ProgrammingExerciseInstructionService,
        private componentFactoryResolver: ComponentFactoryResolver,
        private appRef: ApplicationRef,
        private injector: Injector,
        private modalService: NgbModal,
    ) {}

    set tests(tests: string[]) {
        this.testsValue = tests;
        const [testCaseState, label] = this.programmingExerciseInstructionService.statusForTests(this.tests, this.latestResult);
        this.testCaseState = testCaseState;
        this.testResultLabel = label;
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
        const modalRef = this.modalService.open(EditorInstructionsResultDetailComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.result = this.latestResult;
        modalRef.componentInstance.tests = this.tests;
    }
}
