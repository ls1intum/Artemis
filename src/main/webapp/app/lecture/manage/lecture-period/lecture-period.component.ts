import { Component, computed, model, output, signal } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseTimelineComponent, ExerciseTimelineStatus, TimelineItem } from 'app/exercise/exercise-timeline/exercise-timeline.component';
import { Dayjs } from 'dayjs/esm';

@Component({
    selector: 'jhi-lecture-update-period',
    templateUrl: './lecture-period.component.html',
    imports: [TranslateDirective, ExerciseTimelineComponent],
    styleUrl: './lecture-period.component.scss',
})
export class LectureUpdatePeriodComponent {
    private timelineStatus = signal<ExerciseTimelineStatus>({ valid: true, empty: true });

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
    isPeriodSectionValid = computed(() => this.timelineStatus().valid);

    onTimelineStatusChange(status: ExerciseTimelineStatus) {
        this.timelineStatus.set(status);
        this.timelineStatusChange.emit(status);
    }
}
