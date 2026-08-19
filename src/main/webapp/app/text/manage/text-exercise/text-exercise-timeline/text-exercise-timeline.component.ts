import { Component, input, model, output } from '@angular/core';
import { TimelineStatus } from 'app/shared-ui/timeline/timeline.component';
import { ExerciseUpdateTimelineComponent } from 'app/exercise/exercise-timeline/exercise-update-timeline/exercise-update-timeline.component';
import { Dayjs } from 'dayjs/esm';

@Component({
    selector: 'jhi-text-exercise-timeline',
    imports: [ExerciseUpdateTimelineComponent],
    templateUrl: './text-exercise-timeline.component.html',
})
export class TextExerciseTimelineComponent {
    readonly hasExampleSolution = input(false);
    readonly isImport = input(false);
    readonly lockedToGroup = input(false);
    readonly lockedClick = output<void>();

    readonly releaseDate = model<Dayjs | undefined>();
    readonly startDate = model<Dayjs | undefined>();
    readonly dueDate = model<Dayjs | undefined>();
    readonly assessmentDueDate = model<Dayjs | undefined>();
    readonly exampleSolutionPublicationDate = model<Dayjs | undefined>();

    readonly timelineStatus = output<TimelineStatus>();
}
