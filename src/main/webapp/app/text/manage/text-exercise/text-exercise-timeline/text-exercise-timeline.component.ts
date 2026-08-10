import { Component, input, model, output } from '@angular/core';
import { ExerciseTimelineStatus } from 'app/exercise/exercise-timeline/exercise-timeline.component';
import { ExerciseUpdateTimelineComponent } from 'app/exercise/exercise-timeline/exercise-update-timeline/exercise-update-timeline.component';
import { Dayjs } from 'dayjs/esm';

/**
 * Text exercise flavour of {@link ExerciseUpdateTimelineComponent}. Kept as a named component so the update form
 * (and the e2e page objects) address the timeline of an exercise type through a stable selector.
 */
@Component({
    selector: 'jhi-text-exercise-timeline',
    imports: [ExerciseUpdateTimelineComponent],
    templateUrl: './text-exercise-timeline.component.html',
})
export class TextExerciseTimelineComponent {
    readonly hasExampleSolution = input(false);
    readonly isImport = input(false);

    readonly releaseDate = model<Dayjs | undefined>();
    readonly startDate = model<Dayjs | undefined>();
    readonly dueDate = model<Dayjs | undefined>();
    readonly assessmentDueDate = model<Dayjs | undefined>();
    readonly exampleSolutionPublicationDate = model<Dayjs | undefined>();

    readonly timelineStatus = output<ExerciseTimelineStatus>();
}
