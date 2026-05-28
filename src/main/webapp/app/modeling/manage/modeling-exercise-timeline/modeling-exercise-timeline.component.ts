import { Component, computed, model, output } from '@angular/core';
import { ExerciseTimelineComponent, ExerciseTimelineStatus, TimelineItem } from 'app/shared/exercise-timeline/exercise-timeline.component';
import { Dayjs } from 'dayjs/esm';

@Component({
    selector: 'jhi-modeling-exercise-timeline',
    imports: [ExerciseTimelineComponent],
    templateUrl: './modeling-exercise-timeline.component.html',
    styleUrl: './modeling-exercise-timeline.component.scss',
})
export class ModelingExerciseTimelineComponent {
    releaseDate = model<Dayjs | undefined>();
    startDate = model<Dayjs | undefined>();
    dueDate = model<Dayjs | undefined>();
    assessmentDueDate = model<Dayjs | undefined>();
    timelineItems = computed<TimelineItem[]>(() => this.computeTimelineItems());
    timelineStatus = output<ExerciseTimelineStatus>();

    private computeTimelineItems(): TimelineItem[] {
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
