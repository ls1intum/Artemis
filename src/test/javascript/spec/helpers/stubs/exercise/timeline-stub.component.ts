import { Component, input, output } from '@angular/core';
import { TimelineItem, TimelineStatus } from 'app/shared-ui/timeline/timeline.component';

@Component({
    selector: 'jhi-timeline',
    template: '',
})
export class TimelineStubComponent {
    timelineItems = input.required<TimelineItem[]>();
    timelineStatusChange = output<TimelineStatus>();
}
