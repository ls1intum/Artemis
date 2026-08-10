import { Component, computed, effect, input, model, output } from '@angular/core';
import { Dayjs } from 'dayjs/esm';
import { TimelineComponent, TimelineItem, TimelineStatus, TimelineValidationMode } from 'app/shared-ui/timeline/timeline.component';

@Component({
    selector: 'jhi-exam-timeline',
    imports: [TimelineComponent],
    templateUrl: './exam-timeline.component.html',
})
export class ExamTimelineComponent {
    readonly TimelineValidationMode = TimelineValidationMode;
    readonly testExam = input(false);
    readonly visibleDate = model<Dayjs | undefined>();
    readonly startDate = model<Dayjs | undefined>();
    readonly endDate = model<Dayjs | undefined>();
    readonly timelineStatusChange = output<TimelineStatus>();
    readonly datesChanged = output<void>();

    readonly timelineItems = computed<TimelineItem[]>(() => {
        const testExamKeyPart = this.testExam() ? '.testExam' : '';
        return [
            {
                kind: 'required',
                labelStringKey: 'artemisApp.examManagement.visibleDate',
                date: this.visibleDate,
            },
            {
                kind: 'required',
                labelStringKey: `artemisApp.examManagement${testExamKeyPart}.startDate`,
                date: this.startDate,
            },
            {
                kind: 'required',
                labelStringKey: `artemisApp.examManagement${testExamKeyPart}.endDate`,
                date: this.endDate,
            },
        ];
    });

    constructor() {
        effect(() => {
            this.startDate();
            this.endDate();
            this.datesChanged.emit();
        });
    }
}
