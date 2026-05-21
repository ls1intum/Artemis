import { Component, computed, model } from '@angular/core';
import { Dayjs } from 'dayjs/esm';
import { ExerciseTimeline, TimelineItem } from 'app/shared/exercise-timeline/exercise-timeline';

@Component({
    selector: 'jhi-programming-exercise-timeline',
    imports: [ExerciseTimeline],
    templateUrl: './programming-exercise-timeline.html',
    styleUrl: './programming-exercise-timeline.scss',
})
export class ProgrammingExerciseTimeline {
    releaseDate = model<Dayjs | undefined>();
    startDate = model<Dayjs | undefined>();
    dueDate = model<Dayjs | undefined>();
    buildAndTestStudentSubmissionsAfterDueDate = model<Dayjs | undefined>();
    assessmentDueDate = model<Dayjs | undefined>();
    exampleSolutionPublicationDate = model<Dayjs | undefined>();

    timelineItems = computed<TimelineItem[]>(() => this.computeTimelineItems());

    private computeTimelineItems(): TimelineItem[] {
        const timelineItems: TimelineItem[] = [
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
                labelStringKey: 'artemisApp.exercise.dateForRunningTestsAfterDueDate',
                date: this.buildAndTestStudentSubmissionsAfterDueDate,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.assessmentDueDate',
                date: this.assessmentDueDate,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.exampleSolutionPublicationDate',
                date: this.exampleSolutionPublicationDate,
            },
        ];
        return timelineItems.filter((item) => item.date() !== undefined);
    }
}
