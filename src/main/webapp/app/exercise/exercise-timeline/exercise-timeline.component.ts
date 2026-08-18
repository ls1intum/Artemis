import { Component, computed, input, model, output } from '@angular/core';
import { Dayjs } from 'dayjs/esm';
import { TimelineComponent, TimelineItem, TimelineStatus, TimelineValidationMode } from 'app/shared-ui/timeline/timeline.component';

@Component({
    selector: 'jhi-exercise-timeline',
    imports: [TimelineComponent],
    templateUrl: './exercise-timeline.component.html',
})
export class ExerciseTimelineComponent {
    protected readonly TimelineValidationMode = TimelineValidationMode;

    readonly releaseDate = model<Dayjs | undefined>();
    readonly startDate = model<Dayjs | undefined>();
    readonly dueDate = model<Dayjs | undefined>();
    readonly assessmentDueDate = model<Dayjs | undefined>();
    readonly exampleSolutionPublicationDate = model<Dayjs | undefined>();
    readonly exercisePartOfExerciseGroup = input(false);
    readonly isImport = input(false);
    readonly timelineStatus = output<TimelineStatus>();

    readonly timelineItems = computed<TimelineItem[]>(() => {
        const exercisePartOfExerciseGroup = this.exercisePartOfExerciseGroup();
        const dueDateItem: TimelineItem = {
            kind: 'optional',
            labelStringKey: 'artemisApp.exercise.dueDate',
            date: this.dueDate,
            disabled: exercisePartOfExerciseGroup,
        };

        return [
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.releaseDate',
                date: this.releaseDate,
                disabled: exercisePartOfExerciseGroup,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.startDate',
                date: this.startDate,
                disabled: exercisePartOfExerciseGroup,
            },
            dueDateItem,
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.assessmentDueDate',
                date: this.assessmentDueDate,
                otherRequiredItem: dueDateItem,
                disabled: exercisePartOfExerciseGroup,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.exampleSolutionPublicationDate',
                date: this.exampleSolutionPublicationDate,
                disabled: exercisePartOfExerciseGroup || this.isImport(),
            },
        ];
    });
}
