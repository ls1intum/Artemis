import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ExerciseView, OrionState } from 'app/shared/orion/orion';
import { Router } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ExerciseFilter } from 'app/entities/exercise-filter.model';
import { OrionButtonType } from 'app/shared/orion/orion-button/orion-button.component';

@Component({
    selector: 'jhi-orion-programming-exercise',
    templateUrl: './orion-programming-exercise.component.html',
})
export class OrionProgrammingExerciseComponent implements OnInit {
    private orionConnectorService = inject(OrionConnectorService);
    private router = inject(Router);
    private programmingExerciseService = inject(ProgrammingExerciseService);

    @Input() embedded = false;
    @Input() course: Course;
    @Input() exerciseFilter: ExerciseFilter;
    @Output() exerciseCount = new EventEmitter<number>();
    @Output() filteredExerciseCount = new EventEmitter<number>();

    readonly ExerciseView = ExerciseView;
    protected readonly OrionButtonType = OrionButtonType;
    orionState: OrionState;

    ngOnInit() {
        this.orionConnectorService.state().subscribe((state) => {
            this.orionState = state;
        });
    }

    /**
     * Instructs Orion to download/open the exercise as instructor.
     * Reloads the exercise from the programmingExerciseService to load all data, e.g. the auxiliary repositories which are not loaded in the overview
     *
     * @param programmingExercise to download/open
     */
    editInIDE(programmingExercise: ProgrammingExercise) {
        this.programmingExerciseService.find(programmingExercise.id!).subscribe((res) => this.orionConnectorService.editExercise(res.body!));
    }

    /**
     * Navigates to the ide code editor, should only be done if the exercise is already opened in the ide
     *
     * @param exercise to open
     */
    openOrionEditor(exercise: ProgrammingExercise) {
        try {
            this.router.navigate(['code-editor', 'ide', exercise.id, 'admin', exercise.templateParticipation?.id]);
        } catch (error) {
            this.orionConnectorService.log(error);
        }
    }
}
