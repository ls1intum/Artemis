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
    readonly exampleSolutionPublicationDateErrorStringKey = input<string | undefined>();
    readonly exercisePartOfExerciseGroup = input(false);
    readonly isImport = input(false);
    readonly timelineStatus = output<TimelineStatus>();
    private readonly assessmentDueDateErrorStringKey = computed(() =>
        this.assessmentDueDate() !== undefined && this.dueDate() === undefined ? 'artemisApp.exercise.assessmentDueDateRequiresDueDate' : undefined,
    );

    readonly timelineItems = computed<TimelineItem[]>(() => {
        const exercisePartOfExerciseGroup = this.exercisePartOfExerciseGroup();

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
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.dueDate',
                date: this.dueDate,
                disabled: exercisePartOfExerciseGroup,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.assessmentDueDate',
                date: this.assessmentDueDate,
                errorStringKey: this.assessmentDueDateErrorStringKey,
                disabled: exercisePartOfExerciseGroup,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.exampleSolutionPublicationDate',
                date: this.exampleSolutionPublicationDate,
                errorStringKey: this.exampleSolutionPublicationDateErrorStringKey,
                disabled: exercisePartOfExerciseGroup || this.isImport(),
            },
        ];
    });
}
