import { Component, input, output } from '@angular/core';
import { TimelineStatus, TimelineItem } from 'app/shared-ui/timeline/timeline.component';

@Component({
    selector: 'jhi-timeline',
    template: '',
})
export class ExerciseTimelineStubComponent {
    timelineItems = input.required<TimelineItem[]>();
    readonly = input<boolean>(false);
    lockedToGroup = input<boolean>(false);
    lockedClick = output<void>();
    timelineStatusChange = output<TimelineStatus>();
}
