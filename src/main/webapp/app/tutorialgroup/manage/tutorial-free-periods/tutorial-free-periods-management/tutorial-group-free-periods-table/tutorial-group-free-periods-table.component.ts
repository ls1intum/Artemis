import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { Course } from 'app/core/shared/entities/course.model';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';
import { TutorialGroupFreePeriodRowButtonsComponent } from '../tutorial-group-free-period-row-buttons/tutorial-group-free-period-row-buttons.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-tutorial-group-free-periods-table',
    templateUrl: './tutorial-group-free-periods-table.component.html',
    styleUrls: ['../tutorial-group-free-periods-management.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, NgClass, TutorialGroupFreePeriodRowButtonsComponent, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class TutorialGroupFreePeriodsTableComponent {
    course = input.required<Course>();
    tutorialGroupsConfiguration = input.required<TutorialGroupsConfiguration>();
    tutorialGroupFreePeriods = input.required<TutorialGroupFreePeriod[]>();
    labelText = input.required<string>();
    loadAll = input.required<() => void>();

    protected readonly TutorialGroupFreePeriodsManagementComponent = TutorialGroupFreePeriodsManagementComponent;

    public isInThePast(tutorialGroupFreeDay: TutorialGroupFreePeriod): boolean {
        return tutorialGroupFreeDay.end!.isBefore(dayjs());
    }
}
