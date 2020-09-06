import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exam } from 'app/entities/exam.model';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-create-test-run-modal',
    templateUrl: './create-test-run-modal.component.html',
})
export class CreateTestRunModal implements OnInit {
    exam: Exam;
    testRunConfiguration: { [id: number]: Exercise } = {};
    constructor(private activeModal: NgbActiveModal) {}

    /**
     * Closes the modal and returns the configured testRun
     * @param testRun The test run configured by the instructor
     */
    createTestRun(testRun: StudentExam) {
        this.activeModal.close(testRun);
    }

    /**
     * Sets the selected exercise for an exercise group in the {@link testRunConfiguration}
     * @param exercise Exercise selected for an exercise Group
     */
    onSelectExercise(exercise: Exercise) {
        this.testRunConfiguration[exercise.exerciseGroup!.id] = exercise;
    }

    /**
     * Returns true if an exercise has been selected for every exercise group
     */
    get testRunConfigured(): boolean {
        return Object.keys(this.testRunConfiguration).length === this.exam.exerciseGroups?.length;
    }

    ngOnInit(): void {
        console.log(this.exam.exerciseGroups?.length);
    }
}
