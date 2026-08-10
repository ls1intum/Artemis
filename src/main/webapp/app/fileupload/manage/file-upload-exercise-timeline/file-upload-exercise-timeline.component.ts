import { Component, input, model, output } from '@angular/core';
import { ExerciseTimelineStatus } from 'app/exercise/exercise-timeline/exercise-timeline.component';
import { ExerciseUpdateTimelineComponent } from 'app/exercise/exercise-timeline/exercise-update-timeline/exercise-update-timeline.component';
import { Dayjs } from 'dayjs/esm';

/**
 * File upload exercise flavour of {@link ExerciseUpdateTimelineComponent}. Kept as a named component so the update
 * form (and the e2e page objects) address the timeline of an exercise type through a stable selector.
 */
@Component({
    selector: 'jhi-file-upload-exercise-timeline',
    imports: [ExerciseUpdateTimelineComponent],
    templateUrl: './file-upload-exercise-timeline.component.html',
})
export class FileUploadExerciseTimelineComponent {
    readonly hasExampleSolution = input(false);
    readonly isImport = input(false);
    /** Dates are governed by the exercise's variant group; see {@link ExerciseTimelineComponent}. */
    readonly lockedToGroup = input(false);
    /** Emitted when the user clicks the timeline while {@link lockedToGroup} is set. */
    readonly lockedClick = output<void>();

    readonly releaseDate = model<Dayjs | undefined>();
    readonly startDate = model<Dayjs | undefined>();
    readonly dueDate = model<Dayjs | undefined>();
    readonly assessmentDueDate = model<Dayjs | undefined>();
    readonly exampleSolutionPublicationDate = model<Dayjs | undefined>();

    readonly timelineStatus = output<ExerciseTimelineStatus>();
}
