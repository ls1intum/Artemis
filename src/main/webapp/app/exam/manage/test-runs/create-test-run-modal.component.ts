import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exam } from 'app/entities/exam.model';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';

@Component({
    selector: 'jhi-create-test-run-modal',
    templateUrl: './create-test-run-modal.component.html',
    providers: [ArtemisDurationFromSecondsPipe],
    styles: ['.table tr.active td { background-color:#3e8acc; color: white; }'],
})
export class CreateTestRunModalComponent implements OnInit {
    exam: Exam;
    workingTimeForm: FormGroup;
    testRunConfiguration: { [id: number]: Exercise } = {};

    constructor(private activeModal: NgbActiveModal, private artemisDurationFromSecondsPipe: ArtemisDurationFromSecondsPipe) {}

    ngOnInit(): void {
        this.initWorkingTimeForm();
        this.ignoreEmptyExerciseGroups();
        for (const exerciseGroup of this.exam.exerciseGroups!) {
            if (exerciseGroup.exercises?.length === 1) {
                this.onSelectExercise(exerciseGroup.exercises[0], exerciseGroup);
            }
        }
    }

    /**
     * Creates a test run student exam based on the test run configuration, {@link testRunConfiguration}.
     * To maintain the order of the exercises we must insert them into the testRun configuration in an orderly fashion
     * Closes the modal and returns the configured testRun.
     */
    createTestRun() {
        if (this.testRunConfigured) {
            const testRun = new StudentExam();
            testRun.exam = this.exam;
            testRun.exercises = [];
            // add exercises one by one to maintain exerciseGroup order
            for (const exerciseGroup of this.exam.exerciseGroups!) {
                testRun.exercises.push(this.testRunConfiguration[exerciseGroup.id!]);
            }
            testRun.workingTime = this.workingTimeForm.controls.minutes.value * 60 + this.workingTimeForm.controls.seconds.value;
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
        this.testRunConfiguration[exerciseGroup.id!] = exercise;
    }

    /**
     * Track the items by id on the exercise Tables.
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

    /**
     * Removes the exerciseGroups from the exam which do not contain exercises
     * @private
     */
    private ignoreEmptyExerciseGroups() {
        const exerciseGroupWithExercises: ExerciseGroup[] = [];
        for (const exerciseGroup of this.exam.exerciseGroups!) {
            if (!!exerciseGroup.exercises && exerciseGroup.exercises.length > 0) {
                exerciseGroupWithExercises.push(exerciseGroup);
            }
        }
        this.exam.exerciseGroups = exerciseGroupWithExercises;
    }

    /**
     * Sets up the working time form to display the default working time based on the exam dates
     * @private
     */
    private initWorkingTimeForm() {
        const defaultWorkingTime = this.exam.endDate?.diff(this.exam.startDate, 'seconds');
        const workingTime = this.artemisDurationFromSecondsPipe.transform(defaultWorkingTime ?? 0);
        const workingTimeParts = workingTime.split(':');
        this.workingTimeForm = new FormGroup({
            minutes: new FormControl({ value: parseInt(workingTimeParts[0] ? workingTimeParts[0] : '0', 10), disabled: !!this.exam.visible }, [
                Validators.min(0),
                Validators.required,
            ]),
            seconds: new FormControl({ value: parseInt(workingTimeParts[1] ? workingTimeParts[1] : '0', 10), disabled: !!this.exam.visible }, [
                Validators.min(0),
                Validators.max(59),
                Validators.required,
            ]),
        });
    }
}
