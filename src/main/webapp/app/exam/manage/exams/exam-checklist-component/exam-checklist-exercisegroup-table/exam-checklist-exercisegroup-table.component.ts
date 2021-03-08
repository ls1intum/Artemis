import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-exam-checklist-exercisegroup-table',
    templateUrl: './exam-checklist-exercisegroup-table.component.html',
})
export class ExamChecklistExerciseGroupTableComponent implements OnInit, OnChanges {
    @Input() exerciseGroups: ExerciseGroup[];

    groupsWithoutExercises: ExerciseGroup[] = [];
    groupsWithExercises: Exercise[] = [];

    ngOnInit() {}

    ngOnChanges() {
        if (this.exerciseGroups) {
            this.exerciseGroups.forEach((exerciseGroup) => {
                if (exerciseGroup.exercises) {
                    exerciseGroup.exercises!.forEach((exercise) => {
                        exercise.exerciseGroup = exerciseGroup;
                        this.groupsWithExercises.push(exercise);
                    });
                    const maxPoints = exerciseGroup.exercises?.[0].maxPoints;
                    exerciseGroup.allPointsEqual = exerciseGroup.exercises?.some((exercise) => {
                        return exercise.maxPoints !== maxPoints;
                    });
                } else {
                    this.groupsWithoutExercises.push(exerciseGroup);
                }
            });
        }
    }
}
