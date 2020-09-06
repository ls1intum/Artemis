import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exam } from 'app/entities/exam.model';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

@Component({
    selector: 'jhi-create-test-run-modal',
    templateUrl: './create-test-run-modal.component.html',
    styles: ['.table tr.active td { background-color:#3e8acc; color: white; }'],
})
export class CreateTestRunModal {
    exam: Exam;
    testRunConfiguration: { [id: number]: Exercise } = {};

    constructor(private activeModal: NgbActiveModal) {}

    /**
     * Creates a test run student exam based on the test run configuration, {@link testRunConfiguration}.
     * Closes the modal and returns the configured testRun.
     */
    createTestRun() {
        if (this.testRunConfigured) {
            let testRun = new StudentExam();
            testRun.exam = this.exam;
            testRun.exercises = Object.values(this.testRunConfiguration);
            this.activeModal.close(testRun);
        }
    }

    /**
     * Sets the selected exercise for an exercise group in the testRunConfiguration dictionary, {@link testRunConfiguration}.
     * There, the exerciseGroups' id is used as a key to track the selected exercises for this test run.
     * @param exercise The selected exercise
     * @param exerciseGroup The exercise group for which the user selected an exercise
     */
    onSelectExercise(exercise: Exercise, exerciseGroup: ExerciseGroup) {
        this.testRunConfiguration[exerciseGroup.id] = exercise;
    }

    /**
     * Track the items by id on the exercise Tables
     * @param index {number}
     * @param item {Exercise}
     */
    trackId(index: number, item: Exercise) {
        return item.id;
    }

    /**
     * Returns true if an exercise has been selected for every exercise group
     */
    get testRunConfigured(): boolean {
        return Object.keys(this.testRunConfiguration).length === this.exam.exerciseGroups?.length;
    }

    /**
     * Closes the modal by dismissing it
     */
    cancel() {
        this.activeModal.dismiss('cancel');
    }
}
