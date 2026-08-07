import { Component, computed, input, model, output } from '@angular/core';
import { TimelineComponent, TimelineItem, TimelineStatus } from 'app/shared-ui/timeline/timeline.component';
import { Dayjs } from 'dayjs/esm';

@Component({
    selector: 'jhi-text-exercise-timeline',
    imports: [TimelineComponent],
    templateUrl: './text-exercise-timeline.component.html',
})
export class TextExerciseTimelineComponent {
    releaseDate = model<Dayjs | undefined>();
    startDate = model<Dayjs | undefined>();
    dueDate = model<Dayjs | undefined>();
    assessmentDueDate = model<Dayjs | undefined>();
    exercisePartOfExerciseGroup = input<boolean>(false);
    timelineItems = computed(() => this.buildTimelineItems());
    timelineStatus = output<TimelineStatus>();

    private buildTimelineItems(): TimelineItem[] {
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
        ];
    }
}
