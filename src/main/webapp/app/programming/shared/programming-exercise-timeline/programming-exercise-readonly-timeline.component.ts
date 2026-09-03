import { Component, computed, model } from '@angular/core';
import { Dayjs } from 'dayjs/esm';
import { TimelineComponent, TimelineItem, TimelineValidationMode } from 'app/shared-ui/timeline/timeline.component';

@Component({
    selector: 'jhi-programming-exercise-timeline',
    imports: [TimelineComponent],
    templateUrl: './programming-exercise-readonly-timeline.component.html',
    styleUrl: './programming-exercise-readonly-timeline.component.scss',
})
export class ProgrammingExerciseReadonlyTimelineComponent {
    protected readonly TimelineValidationMode = TimelineValidationMode;

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
                disabled: true,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.startDate',
                date: this.startDate,
                disabled: true,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.dueDate',
                date: this.dueDate,
                disabled: true,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.dateForRunningTestsAfterDueDate',
                date: this.buildAndTestStudentSubmissionsAfterDueDate,
                disabled: true,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.assessmentDueDate',
                date: this.assessmentDueDate,
                disabled: true,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.exampleSolutionPublicationDate',
                date: this.exampleSolutionPublicationDate,
                disabled: true,
            },
        ];
        return timelineItems.filter((item) => item.date() !== undefined);
    }
}
