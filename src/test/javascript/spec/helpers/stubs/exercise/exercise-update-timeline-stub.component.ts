import { Component, input, model, output } from '@angular/core';
import { Dayjs } from 'dayjs/esm';
import { ExerciseTimelineStatus } from 'app/exercise/exercise-timeline/exercise-timeline.component';

@Component({
    selector: 'jhi-exercise-update-timeline',
    template: '',
})
export class ExerciseUpdateTimelineStubComponent {
    readonly hasExampleSolution = input(false);
    readonly isImport = input(false);

    readonly releaseDate = model<Dayjs | undefined>();
    readonly startDate = model<Dayjs | undefined>();
    readonly dueDate = model<Dayjs | undefined>();
    readonly assessmentDueDate = model<Dayjs | undefined>();
    readonly exampleSolutionPublicationDate = model<Dayjs | undefined>();

    readonly timelineStatus = output<ExerciseTimelineStatus>();
}
