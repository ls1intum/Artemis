import { ChangeDetectionStrategy, Component, Input, OnDestroy, inject } from '@angular/core';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { TutorialGroupFreePeriodFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { finalize, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { Course } from 'app/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';
import { captureException } from '@sentry/angular';

@Component({
    selector: 'jhi-create-tutorial-group-free-day',
    templateUrl: './create-tutorial-group-free-period.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateTutorialGroupFreePeriodComponent implements OnDestroy {
    private activeModal = inject(NgbActiveModal);
    private tutorialGroupFreePeriodService = inject(TutorialGroupFreePeriodService);
    private alertService = inject(AlertService);

    ngUnsubscribe = new Subject<void>();

    tutorialGroupFreePeriodToCreate: TutorialGroupFreePeriodDTO = new TutorialGroupFreePeriodDTO();
    isLoading: boolean;

    @Input()
    tutorialGroupConfigurationId: number;

    @Input()
    course: Course;

    isInitialized = false;

    initialize() {
        if (!this.tutorialGroupConfigurationId || !this.course) {
            console.error('Error: Component not fully configured');
        } else {
            this.isInitialized = true;
        }
    }
    createTutorialGroupFreePeriod(formData: TutorialGroupFreePeriodFormData) {
        const { startDate, endDate, startTime, endTime, reason } = formData;

        this.tutorialGroupFreePeriodToCreate.startDate = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(startDate, startTime, undefined);
        this.tutorialGroupFreePeriodToCreate.endDate = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(endDate, endTime, startDate);
        this.tutorialGroupFreePeriodToCreate.reason = reason;

        this.isLoading = true;
        this.tutorialGroupFreePeriodService
            .create(this.course.id!, this.tutorialGroupConfigurationId, this.tutorialGroupFreePeriodToCreate)
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
                    onError(this.alertService, res);
                    this.clear();
                },
            });
    }

    /**
     * This static method combines a date and time into a single Date object. If the date is not provided, it uses an alternative date.
     * It is used to handle the start and end date of a freePeriod, a freeDay or a freePeriodWithinDay.
     *
     * @param {Date} date - The date to be combined with the time. If not provided, the method uses the alternative date. If the provided Date is the startDate, the alternativeDate should be left undefined
     * @param {Date} time - The time to be combined with the date. If not provided, the method sets the time to 23:59 for the alternative date or 0:00 for the date.
     * @param {Date} alternativeDate - The alternative date to be used if the date is not provided.
     * @returns {Date} - The combined date and time as a Date object.
     * @throws {Error} - Throws an error if both date and time are undefined.
     */
    public static combineDateAndTimeWithAlternativeDate(date?: Date, time?: Date, alternativeDate?: Date): Date {
        if (!date) {
            // This is the case it is the endDate of a freeDay or a freePeriodWithinDay
            if (!alternativeDate) {
                const error = new Error('date and time are undefined');
                captureException(error);
                throw error;
            }
            const resDate = new Date(alternativeDate);
            resDate.setHours(time?.getHours() ?? 23, time?.getMinutes() ?? 59);
            return resDate;
        }
        date.setHours(time?.getHours() ?? (alternativeDate ? 23 : 0), time?.getMinutes() ?? (alternativeDate ? 59 : 0));
        return date;
    }

    clear() {
        this.activeModal.dismiss();
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
