import { ChangeDetectionStrategy, Component, OnDestroy, inject, input, output, signal } from '@angular/core';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodFormData } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { Subject, finalize } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/course/shared/entities/course.model';
import { takeUntil } from 'rxjs/operators';
import { CreateTutorialGroupFreePeriodComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';
import { LoadingIndicatorContainerComponent } from 'app/shared-ui/loading-indicator-container/loading-indicator-container.component';
import { TutorialGroupFreePeriodFormComponent } from '../tutorial-free-period-form/tutorial-group-free-period-form.component';
import { captureException } from '@sentry/angular';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodService } from 'app/tutorialgroup/manage/service/tutorial-group-free-period.service';
import { DialogModule } from 'primeng/dialog';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-edit-tutorial-group-free-period',
    templateUrl: './edit-tutorial-group-free-period.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, TutorialGroupFreePeriodFormComponent, DialogModule, ArtemisTranslatePipe],
})
export class EditTutorialGroupFreePeriodComponent implements OnDestroy {
    private tutorialGroupFreePeriodService = inject(TutorialGroupFreePeriodService);
    private alertService = inject(AlertService);

    readonly dialogVisible = signal<boolean>(false);
    readonly freePeriodUpdated = output<void>();

    readonly tutorialGroupFreePeriod = input.required<TutorialGroupFreePeriod>();
    readonly tutorialGroupsConfiguration = input.required<TutorialGroupsConfiguration>();
    readonly course = input.required<Course>();

    readonly isLoading = signal<boolean>(false);

    ngUnsubscribe = new Subject<void>();
    readonly formData = signal<TutorialGroupFreePeriodFormData>(undefined!);

    /**
     * Opens the dialog and sets up the form data based on the tutorial group free period, course, and tutorial groups configuration.
     * If any of these are not provided, it logs an error and returns early.
     * It determines whether the tutorial group free period is a freePeriod, a freeDay, or a freePeriodWithinDay.
     * Based on these determinations, it sets up the form data accordingly.
     */
    open() {
        if (!this.tutorialGroupFreePeriod() || !this.course() || !this.tutorialGroupsConfiguration()) {
            captureException('Error: Component not fully configured');
            return;
        }

        const freePeriod = this.tutorialGroupFreePeriod();
        const courseValue = this.course();
        const isFreePeriod = TutorialGroupFreePeriodsManagementComponent.isFreePeriod(freePeriod);
        const isFreePeriodWithinDay = TutorialGroupFreePeriodsManagementComponent.isFreePeriodWithinDay(freePeriod);

        const formData: TutorialGroupFreePeriodFormData = {
            startDate: freePeriod.start?.tz(courseValue.timeZone).toDate(),
            endDate: isFreePeriod ? freePeriod.end?.tz(courseValue.timeZone).toDate() : undefined,
            startTime: isFreePeriodWithinDay ? freePeriod.start?.tz(courseValue.timeZone).toDate() : undefined,
            endTime: isFreePeriodWithinDay ? freePeriod.end?.tz(courseValue.timeZone).toDate() : undefined,
            reason: freePeriod.reason,
        };

        if (isFreePeriodWithinDay) {
            const tutorialGroupFreePeriodStart = freePeriod.start;
            const tutorialGroupFreePeriodEnd = freePeriod.end;
            if (formData.startTime && tutorialGroupFreePeriodStart) {
                formData.startTime.setHours(tutorialGroupFreePeriodStart.tz(courseValue.timeZone).hour());
            }
            if (formData.endTime && tutorialGroupFreePeriodEnd) {
                formData.endTime.setHours(tutorialGroupFreePeriodEnd.tz(courseValue.timeZone).hour());
            }
        }

        this.formData.set(formData);
        this.dialogVisible.set(true);
    }

    close(): void {
        this.dialogVisible.set(false);
    }

    updateTutorialGroupFreePeriod(formData: TutorialGroupFreePeriodFormData) {
        const { startDate, endDate, startTime, endTime, reason } = formData;

        const tutorialGroupFreePeriodDto = new TutorialGroupFreePeriodDTO();
        tutorialGroupFreePeriodDto.startDate = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(startDate, startTime, undefined);
        tutorialGroupFreePeriodDto.endDate = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(endDate, endTime, startDate);
        tutorialGroupFreePeriodDto.reason = reason;

        this.isLoading.set(true);
        this.tutorialGroupFreePeriodService
            .update(this.course().id!, this.tutorialGroupsConfiguration().id!, this.tutorialGroupFreePeriod().id!, tutorialGroupFreePeriodDto)
            .pipe(
                finalize(() => {
                    this.isLoading.set(false);
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: () => {
                    this.close();
                    this.freePeriodUpdated.emit();
                },
                error: (res: HttpErrorResponse) => {
                    this.close();
                    onError(this.alertService, res);
                },
            });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
