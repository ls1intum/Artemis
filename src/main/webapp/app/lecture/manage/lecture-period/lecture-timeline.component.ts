import { Component, effect, model, output } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TimelineComponent, TimelineItem, TimelineStatus } from 'app/shared-ui/timeline/timeline.component';
import { Dayjs } from 'dayjs/esm';

@Component({
    selector: 'jhi-lecture-timeline',
    templateUrl: './lecture-timeline.component.html',
    imports: [TranslateDirective, TimelineComponent],
    styleUrl: './lecture-timeline.component.scss',
})
export class LectureTimelineComponent {
    startDate = model<Dayjs | undefined>();
    endDate = model<Dayjs | undefined>();
    timelineStatusChange = output<TimelineStatus>();
    datesChanged = output<void>();
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

    constructor() {
        effect(() => {
            this.startDate();
            this.endDate();
            this.datesChanged.emit();
        });
    }
}
