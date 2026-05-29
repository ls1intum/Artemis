import { Component, model, output } from '@angular/core';
import { ExerciseTimelineComponent, ExerciseTimelineStatus, TimelineItem } from 'app/exercise/exercise-timeline/exercise-timeline.component';
import { Dayjs } from 'dayjs/esm';

@Component({
    selector: 'jhi-modeling-exercise-timeline',
    imports: [ExerciseTimelineComponent],
    templateUrl: './modeling-exercise-timeline.component.html',
})
export class ModelingExerciseTimelineComponent {
    releaseDate = model<Dayjs | undefined>();
    startDate = model<Dayjs | undefined>();
    dueDate = model<Dayjs | undefined>();
    assessmentDueDate = model<Dayjs | undefined>();
    timelineItems = this.buildTimelineItems();
    timelineStatus = output<ExerciseTimelineStatus>();

    private buildTimelineItems(): TimelineItem[] {
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
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.dueDate',
                date: this.dueDate,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.assessmentDueDate',
                date: this.assessmentDueDate,
            },
        ];
    }
}
