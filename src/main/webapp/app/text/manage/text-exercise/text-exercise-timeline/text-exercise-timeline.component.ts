import { Component, input, model, output } from '@angular/core';
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
    /** When true the dates are governed by the exercise's variant group (see {@link TimelineComponent}). */
    lockedToGroup = input<boolean>(false);
    /** Emitted when the user clicks the timeline while {@link lockedToGroup} is set. */
    lockedClick = output<void>();
    timelineItems = this.buildTimelineItems();
    timelineStatus = output<TimelineStatus>();

    private buildTimelineItems(): TimelineItem[] {
        const dueDateItem: TimelineItem = {
            kind: 'optional',
            labelStringKey: 'artemisApp.exercise.dueDate',
            date: this.dueDate,
        };

        return [
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.releaseDate',
                date: this.releaseDate,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.startDate',
                date: this.startDate,
            },
            dueDateItem,
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.assessmentDueDate',
                date: this.assessmentDueDate,
                otherRequiredItem: dueDateItem,
            },
        ];
    }
}
