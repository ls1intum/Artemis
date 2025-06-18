import { ChangeDetectionStrategy, Component, OnDestroy, inject, input } from '@angular/core';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodFormData } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { Subject, finalize } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { takeUntil } from 'rxjs/operators';
import { CreateTutorialGroupFreePeriodComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupFreePeriodFormComponent } from '../tutorial-free-period-form/tutorial-group-free-period-form.component';
import { captureException } from '@sentry/angular';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodService } from 'app/tutorialgroup/shared/service/tutorial-group-free-period.service';

@Component({
    selector: 'jhi-edit-tutorial-group-free-period',
    templateUrl: './edit-tutorial-group-free-period.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, TranslateDirective, TutorialGroupFreePeriodFormComponent],
})
export class EditTutorialGroupFreePeriodComponent implements OnDestroy {
    private activeModal = inject(NgbActiveModal);
    private tutorialGroupFreePeriodService = inject(TutorialGroupFreePeriodService);
    private alertService = inject(AlertService);

    readonly tutorialGroupFreePeriod = input<TutorialGroupFreePeriod>();

    readonly tutorialGroupsConfiguration = input<TutorialGroupsConfiguration>();

    readonly course = input<Course>();

    isLoading = false;

    isInitialized = false;

    ngUnsubscribe = new Subject<void>();
    formData: TutorialGroupFreePeriodFormData;

    /**
     * Initializes the component by setting up the form data based on the tutorial group free period, course, and tutorial groups configuration.
     * If any of these are not provided, it logs an error and returns early.
     * It determines whether the tutorial group free period is a freePeriod, a freeDay, or a freePeriodWithinDay.
     * Based on these determinations, it sets up the form data accordingly.
     */
    initialize() {
        const tutorialGroupFreePeriod = this.tutorialGroupFreePeriod();
        const course = this.course();
        if (!tutorialGroupFreePeriod || !course || !this.tutorialGroupsConfiguration()) {
            captureException('Error: Component not fully configured');
            return;
        }

        const isFreePeriod = TutorialGroupFreePeriodsManagementComponent.isFreePeriod(tutorialGroupFreePeriod);
        const isFreePeriodWithinDay = TutorialGroupFreePeriodsManagementComponent.isFreePeriodWithinDay(tutorialGroupFreePeriod);

        this.formData = {
            startDate: tutorialGroupFreePeriod.start?.tz(course.timeZone).toDate(),
            endDate: isFreePeriod ? tutorialGroupFreePeriod.end?.tz(course.timeZone).toDate() : undefined,
            startTime: isFreePeriodWithinDay ? tutorialGroupFreePeriod.start?.tz(course.timeZone).toDate() : undefined,
            endTime: isFreePeriodWithinDay ? tutorialGroupFreePeriod.end?.tz(course.timeZone).toDate() : undefined,
            reason: tutorialGroupFreePeriod.reason,
        };

        if (isFreePeriodWithinDay) {
            const tutorialGroupFreePeriodStart = tutorialGroupFreePeriod.start;
            const tutorialGroupFreePeriodEnd = tutorialGroupFreePeriod.end;
            if (this.formData.startTime && tutorialGroupFreePeriodStart) {
                this.formData.startTime.setHours(tutorialGroupFreePeriodStart.tz(course.timeZone).hour());
            }
            if (this.formData.endTime && tutorialGroupFreePeriodEnd) {
                this.formData.endTime.setHours(tutorialGroupFreePeriodEnd.tz(course.timeZone).hour());
            }
        }

        this.isInitialized = true;
    }

    updateTutorialGroupFreePeriod(formData: TutorialGroupFreePeriodFormData) {
        const { startDate, endDate, startTime, endTime, reason } = formData;

        const tutorialGroupFreePeriodDto = new TutorialGroupFreePeriodDTO();
        tutorialGroupFreePeriodDto.startDate = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(startDate, startTime, undefined);
        tutorialGroupFreePeriodDto.endDate = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(endDate, endTime, startDate);
        tutorialGroupFreePeriodDto.reason = reason;

        this.isLoading = true;
        const tutorialGroupFreePeriod = this.tutorialGroupFreePeriod();
        const tutorialGroupsConfiguration = this.tutorialGroupsConfiguration();
        const course = this.course();
        if (!tutorialGroupFreePeriod || !course || !tutorialGroupsConfiguration) {
            captureException('Error: Component not fully configured');
            return;
        }
        this.tutorialGroupFreePeriodService
            .update(course.id!, tutorialGroupsConfiguration.id!, tutorialGroupFreePeriod.id!, tutorialGroupFreePeriodDto)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: () => {
                    this.activeModal.close();
                },
                error: (res: HttpErrorResponse) => {
                    this.clear();
                    onError(this.alertService, res);
                },
            });
    }

    clear() {
        this.activeModal.dismiss();
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
