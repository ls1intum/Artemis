import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { Course } from 'app/entities/course.model';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';

@Component({
    selector: 'jhi-tutorial-group-free-periods-table',
    templateUrl: './tutorial-group-free-periods-table.component.html',
    styleUrls: ['../tutorial-group-free-periods-management.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorialGroupFreePeriodsTableComponent {
    @Input() course: Course;
    @Input() tutorialGroupsConfiguration: TutorialGroupsConfiguration;
    @Input() tutorialGroupFreePeriods: TutorialGroupFreePeriod[];
    @Input() labelText: string;
    @Input() loadAll: () => void;

    protected readonly TutorialGroupFreePeriodsManagementComponent = TutorialGroupFreePeriodsManagementComponent;

    public isInThePast(tutorialGroupFreeDay: TutorialGroupFreePeriod): boolean {
        return tutorialGroupFreeDay.end!.isBefore(dayjs());
    }
}
