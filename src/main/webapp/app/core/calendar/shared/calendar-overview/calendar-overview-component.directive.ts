import { Directive, Signal, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { distinctUntilChanged, finalize, map } from 'rxjs/operators';
import { Dayjs } from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { ActivatedRoute } from '@angular/router';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { faChevronLeft, faChevronRight, faXmark } from '@fortawesome/free-solid-svg-icons';

@Directive()
export abstract class CalendarOverviewComponent {
    private activatedRoute = inject(ActivatedRoute);

    protected calendarService = inject(CalendarService);
    protected translateService = inject(TranslateService);
    protected locale = getCurrentLocaleSignal(this.translateService);

    protected readonly faXmark = faXmark;
    protected readonly faChevronRight = faChevronRight;
    protected readonly faChevronLeft = faChevronLeft;

    abstract firstDateOfCurrentMonth: Signal<Dayjs>;
    abstract monthDescription: Signal<string>;

    calendarSubscriptionToken = this.calendarService.subscriptionToken;
    courseId: Signal<number | undefined> = this.getCurrentCourseIdSignal();
    isLoading = signal<boolean>(false);

    constructor() {
        effect(() => {
            if (this.courseId() !== undefined) {
                this.loadEventsForCurrentMonth();
            }
        });
    }

    abstract goToPrevious(): void;

    abstract goToNext(): void;

    abstract goToToday(): void;

    protected loadEventsForCurrentMonth(): void {
        const courseId = this.courseId();
        if (!courseId) return;
        this.isLoading.set(true);
        this.calendarService
            .loadEventsForCurrentMonth(courseId, this.firstDateOfCurrentMonth())
            .pipe(finalize(() => this.isLoading.set(false)))
            .subscribe();
    }

    private getCurrentCourseIdSignal(): Signal<number | undefined> {
        return toSignal(
            this.activatedRoute.parent!.paramMap.pipe(
                map((parameterMap) => {
                    const courseIdParameter = parameterMap.get('courseId');
                    return courseIdParameter !== null ? Number(courseIdParameter) : undefined;
                }),
                distinctUntilChanged(),
            ),
            { initialValue: undefined },
        );
    }
}
