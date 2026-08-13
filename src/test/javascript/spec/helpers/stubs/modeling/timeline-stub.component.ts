import { Component, input, output } from '@angular/core';
import { TimelineItem, TimelineStatus, TimelineValidationMode } from 'app/shared-ui/timeline/timeline.component';

@Component({
    selector: 'jhi-timeline',
    template: '',
})
export class TimelineStubComponent {
    timelineItems = input.required<TimelineItem[]>();
    readonly = input<boolean>(false);
    validationMode = input<TimelineValidationMode>(TimelineValidationMode.SEQUENTIALLY_ALLOW_EQUAL);
    timelineStatusChange = output<TimelineStatus>();
}
