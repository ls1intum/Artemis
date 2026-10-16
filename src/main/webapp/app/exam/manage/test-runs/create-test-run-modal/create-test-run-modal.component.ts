import { Component, OnInit, computed, inject, input, model, output, signal } from '@angular/core';
import { TumAetUiButtonDirective, TumAetUiDialogComponent, TumAetUiInputDirective, TumAetUiTableDirective } from '@tumaet/ui-angular';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CreateTestRunDTO } from 'app/exam/manage/test-runs/create-test-run-dto.model';

@Component({
    selector: 'jhi-create-test-run-modal',
    templateUrl: './create-test-run-modal.component.html',
    providers: [ArtemisDurationFromSecondsPipe],
    imports: [
        TranslateDirective,
        ArtemisTranslatePipe,
        FormsModule,
        ReactiveFormsModule,
        TumAetUiButtonDirective,
        TumAetUiDialogComponent,
        TumAetUiInputDirective,
        TumAetUiTableDirective,
    ],
})
export class CreateTestRunModalComponent implements OnInit {
    private artemisDurationFromSecondsPipe = inject(ArtemisDurationFromSecondsPipe);

    readonly exam = input.required<Exam>();
    readonly visible = model(true);
    readonly testRunCreate = output<CreateTestRunDTO>();

    workingTimeForm!: FormGroup; // initialized in ngOnInit() via initWorkingTimeForm()
    /** The selected exercise per exercise group id. */
    readonly testRunConfiguration = signal<Map<number, Exercise>>(new Map<number, Exercise>());
    /** The exercise groups of the exam which contain exercises; groups without exercises cannot be part of a test run. */
    readonly exerciseGroups = computed(() => (this.exam().exerciseGroups ?? []).filter((exerciseGroup) => !!exerciseGroup.exercises && exerciseGroup.exercises.length > 0));
    /** True if an exercise has been selected for every exercise group */
    readonly testRunConfigured = computed(() => this.testRunConfiguration().size === this.exerciseGroups().length);

    ngOnInit(): void {
        this.initWorkingTimeForm();
        for (const exerciseGroup of this.exerciseGroups()) {
            if (exerciseGroup.exercises?.length === 1) {
                this.onSelectExercise(exerciseGroup.exercises[0], exerciseGroup);
            }
        }
    }

    /**
     * Creates a test run student exam based on the test run configuration, {@link testRunConfiguration}.
     * To maintain the order of the exercises we must insert them into the testRun configuration in an orderly fashion
     * Closes the modal and emits the configured testRun.
     */
    createTestRun() {
        if (!this.testRunConfigured()) return;
        // add exercise ids one by one to maintain exerciseGroup order (the server persists them in this exact order)
        const exerciseIds = this.exerciseGroups().map((exerciseGroup) => this.testRunConfiguration().get(exerciseGroup.id!)!.id!);
        const testRun: CreateTestRunDTO = {
            examId: this.exam().id!,
            exerciseIds,
            workingTime: this.workingTimeForm.controls.minutes.value * 60 + this.workingTimeForm.controls.seconds.value,
        };
        this.testRunCreate.emit(testRun);
        this.visible.set(false);
    }

    /**
     * Sets the selected exercise for an exercise group in the testRunConfiguration, {@link testRunConfiguration}.
     * There, the exerciseGroups' id is used as a key to track the selected exercises for this test run.
     * @param exercise The selected exercise
     * @param exerciseGroup The exercise group for which the user selected an exercise
     */
    onSelectExercise(exercise: Exercise, exerciseGroup: ExerciseGroup) {
        this.testRunConfiguration.update((configuration) => new Map(configuration).set(exerciseGroup.id!, exercise));
    }

    /**
     * Returns true if the exercise is the one currently selected for its exercise group
     */
    isSelected(exercise: Exercise, exerciseGroup: ExerciseGroup): boolean {
        return !!exerciseGroup.id && exercise.id === this.testRunConfiguration().get(exerciseGroup.id)?.id;
    }

    /**
     * Closes the modal by dismissing it
     */
    cancel() {
        this.visible.set(false);
    }

    /**
     * Sets up the working time form to display the default working time based on the exam dates
     */
    private initWorkingTimeForm() {
        const currentExam = this.exam();
        const defaultWorkingTime = currentExam.endDate?.diff(currentExam.startDate, 'seconds');
        const workingTime = this.artemisDurationFromSecondsPipe.transform(defaultWorkingTime ?? 0);
        const workingTimeParts = workingTime.split(':');
        this.workingTimeForm = new FormGroup({
            minutes: new FormControl({ value: parseInt(workingTimeParts[0] ? workingTimeParts[0] : '0', 10), disabled: !!currentExam.visible }, [
                Validators.min(0),
                Validators.required,
            ]),
            seconds: new FormControl({ value: parseInt(workingTimeParts[1] ? workingTimeParts[1] : '0', 10), disabled: !!currentExam.visible }, [
                Validators.min(0),
                Validators.max(59),
                Validators.required,
            ]),
        });
    }
}
