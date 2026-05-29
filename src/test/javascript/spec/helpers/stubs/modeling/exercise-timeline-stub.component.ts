import { Component, input, output } from '@angular/core';
import { ExerciseTimelineStatus, TimelineItem } from 'app/exercise/exercise-timeline/exercise-timeline.component';

@Component({
    selector: 'jhi-exercise-timeline',
    template: '',
})
export class ExerciseTimelineStubComponent {
    timelineItems = input.required<TimelineItem[]>();
    timelineStatusChange = output<ExerciseTimelineStatus>();
}
