import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { Course } from 'app/entities/course.model';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-tutorial-group-free-periods-table',
    templateUrl: './tutorial-group-free-periods-table.component.html',
    styleUrl: '../tutorial-group-free-periods-management.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorialGroupFreePeriodsTableComponent {
    @Input() course: Course;
    @Input() tutorialGroupsConfiguration: TutorialGroupsConfiguration;
    @Input() tutorialGroupFreePeriods: TutorialGroupFreePeriod[];
    @Input() lableText: string;
    @Input() loadAll: () => void;

    public isInThePast(tutorialGroupFreeDay: TutorialGroupFreePeriod): boolean {
        return tutorialGroupFreeDay.end!.isBefore(dayjs());
    }
}
