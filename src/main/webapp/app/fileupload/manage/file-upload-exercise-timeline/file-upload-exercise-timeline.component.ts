import { Component, input, model, output } from '@angular/core';
import { ExerciseTimelineComponent, ExerciseTimelineStatus, TimelineItem } from 'app/exercise/exercise-timeline/exercise-timeline.component';
import { Dayjs } from 'dayjs/esm';

@Component({
    selector: 'jhi-file-upload-exercise-timeline',
    imports: [ExerciseTimelineComponent],
    templateUrl: './file-upload-exercise-timeline.component.html',
})
export class FileUploadExerciseTimelineComponent {
    releaseDate = model<Dayjs | undefined>();
    startDate = model<Dayjs | undefined>();
    dueDate = model<Dayjs | undefined>();
    assessmentDueDate = model<Dayjs | undefined>();
    /** When true the dates are governed by the exercise's variant group (see {@link ExerciseTimelineComponent}). */
    lockedToGroup = input<boolean>(false);
    /** Emitted when the user clicks the timeline while {@link lockedToGroup} is set. */
    lockedClick = output<void>();
    timelineItems = this.buildTimelineItems();
    timelineStatus = output<ExerciseTimelineStatus>();

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
