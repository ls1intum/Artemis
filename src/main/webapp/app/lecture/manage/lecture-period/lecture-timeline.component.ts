import { Component, model, output } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseTimelineComponent, ExerciseTimelineStatus, TimelineItem } from 'app/exercise/exercise-timeline/exercise-timeline.component';
import { Dayjs } from 'dayjs/esm';

@Component({
    selector: 'jhi-lecture-update-period',
    templateUrl: './lecture-timeline.component.html',
    imports: [TranslateDirective, ExerciseTimelineComponent],
    styleUrl: './lecture-timeline.component.scss',
})
export class LectureTimelineComponent {
    startDate = model<Dayjs | undefined>();
    endDate = model<Dayjs | undefined>();
    timelineStatusChange = output<ExerciseTimelineStatus>();
    timelineItems: TimelineItem[] = [
        {
            kind: 'optional',
            labelStringKey: 'artemisApp.lecture.startDate',
            date: this.startDate,
        },
        {
            kind: 'optional',
            labelStringKey: 'artemisApp.lecture.endDate',
            date: this.endDate,
        },
    ];
}
