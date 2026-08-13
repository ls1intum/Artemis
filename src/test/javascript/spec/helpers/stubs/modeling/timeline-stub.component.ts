import { Component, input, output } from '@angular/core';
import { TimelineStatus, TimelineItem } from 'app/shared-ui/timeline/timeline.component';

@Component({
    selector: 'jhi-timeline',
    template: '',
})
export class TimelineStubComponent {
    timelineItems = input.required<TimelineItem[]>();
    readonly = input<boolean>(false);
    strictOrdering = input(false);
    timelineStatusChange = output<TimelineStatus>();
}
