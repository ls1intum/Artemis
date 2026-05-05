import { Component, OnInit, inject, signal } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-create-test-run-modal',
    templateUrl: './create-test-run-modal.component.html',
    providers: [ArtemisDurationFromSecondsPipe],
    styles: ['.table tr.active td { background-color:#3e8acc; color: white; }'],
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule],
})
export class CreateTestRunModalComponent implements OnInit {
    private dialogRef = inject(DynamicDialogRef);
    private dialogConfig = inject(DynamicDialogConfig);
    private artemisDurationFromSecondsPipe = inject(ArtemisDurationFromSecondsPipe);

    exam = signal<Exam | undefined>(undefined);
    workingTimeForm: FormGroup;
    testRunConfiguration: { [id: number]: Exercise } = {};

    ngOnInit(): void {
        const data = this.dialogConfig?.data;
        if (data?.exam) {
            this.exam.set(data.exam);
        }

        this.initWorkingTimeForm();
        this.ignoreEmptyExerciseGroups();
        const currentExam = this.exam();
        if (currentExam) {
            for (const exerciseGroup of currentExam.exerciseGroups!) {
                if (exerciseGroup.exercises?.length === 1) {
                    this.onSelectExercise(exerciseGroup.exercises[0], exerciseGroup);
                }
            }
        }
    }

    /**
     * Creates a test run student exam based on the test run configuration, {@link testRunConfiguration}.
     * To maintain the order of the exercises we must insert them into the testRun configuration in an orderly fashion
     * Closes the modal and returns the configured testRun.
     */
    createTestRun() {
        const currentExam = this.exam();
        if (!currentExam) return;
        if (this.testRunConfigured) {
            const testRun = new StudentExam();
            testRun.exam = currentExam;
            testRun.exercises = [];
            // add exercises one by one to maintain exerciseGroup order
            for (const exerciseGroup of currentExam.exerciseGroups!) {
                testRun.exercises.push(this.testRunConfiguration[exerciseGroup.id!]);
            }
            testRun.workingTime = this.workingTimeForm.controls.minutes.value * 60 + this.workingTimeForm.controls.seconds.value;
            this.dialogRef.close(testRun);
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
     * Returns true if an exercise has been selected for every exercise group
     */
    get testRunConfigured(): boolean {
        return Object.keys(this.testRunConfiguration).length === this.exam()?.exerciseGroups?.length;
    }

    /**
     * Closes the modal by dismissing it
     */
    cancel() {
        this.dialogRef.close();
    }

    /**
     * Removes the exerciseGroups from the exam which do not contain exercises
     */
    private ignoreEmptyExerciseGroups() {
        const currentExam = this.exam();
        if (!currentExam) return;
        const exerciseGroupWithExercises: ExerciseGroup[] = [];
        for (const exerciseGroup of currentExam.exerciseGroups!) {
            if (!!exerciseGroup.exercises && exerciseGroup.exercises.length > 0) {
                exerciseGroupWithExercises.push(exerciseGroup);
            }
        }
        currentExam.exerciseGroups = exerciseGroupWithExercises;
    }

    /**
     * Sets up the working time form to display the default working time based on the exam dates
     */
    private initWorkingTimeForm() {
        const currentExam = this.exam();
        const defaultWorkingTime = currentExam?.endDate?.diff(currentExam?.startDate, 'seconds');
        const workingTime = this.artemisDurationFromSecondsPipe.transform(defaultWorkingTime ?? 0);
        const workingTimeParts = workingTime.split(':');
        this.workingTimeForm = new FormGroup({
            minutes: new FormControl({ value: parseInt(workingTimeParts[0] ? workingTimeParts[0] : '0', 10), disabled: !!currentExam?.visible }, [
                Validators.min(0),
                Validators.required,
            ]),
            seconds: new FormControl({ value: parseInt(workingTimeParts[1] ? workingTimeParts[1] : '0', 10), disabled: !!currentExam?.visible }, [
                Validators.min(0),
                Validators.max(59),
                Validators.required,
            ]),
        });
    }
}
