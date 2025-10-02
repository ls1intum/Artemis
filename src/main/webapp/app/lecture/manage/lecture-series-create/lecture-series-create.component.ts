import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { FloatLabelModule } from 'primeng/floatlabel';
import { InputMaskModule } from 'primeng/inputmask';
import { ButtonModule } from 'primeng/button';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPenToSquare, faXmark } from '@fortawesome/free-solid-svg-icons';
import dayjs, { Dayjs } from 'dayjs/esm';
import { LectureSeriesEditModalComponent } from 'app/lecture/manage/lecture-series-edit-modal/lecture-series-edit-modal.component';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { AlertService } from 'app/shared/service/alert.service';
import { Router } from '@angular/router';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { LectureCreateDTO } from 'app/lecture/shared/entities/lecture.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { addOneMinuteTo, isFirstAfterOrEqualSecond } from 'app/lecture/manage/util/lecture-management.utils';
import { finalize } from 'rxjs';

type WeekdayIndex = 1 | 2 | 3 | 4 | 5 | 6 | 7;

interface WeekdayOption {
    label: string;
    weekdayIndex: WeekdayIndex;
}

export interface LectureDraft {
    key: string;
    state: LectureDraftState;
    dto: LectureCreateDTO;
}

export enum LectureDraftState {
    EDITED = 'edited',
    DELETED = 'deleted',
    REGULAR = 'regular',
}

@Component({
    selector: 'jhi-lecture-series-create',
    imports: [
        SelectModule,
        FormsModule,
        DatePickerModule,
        FloatLabelModule,
        InputMaskModule,
        ButtonModule,
        FaIconComponent,
        LectureSeriesEditModalComponent,
        NgClass,
        TranslateDirective,
    ],
    templateUrl: './lecture-series-create.component.html',
    styleUrl: './lecture-series-create.component.scss',
})
export class LectureSeriesCreateComponent {
    private lectureService = inject(LectureService);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);
    private router = inject(Router);
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    protected readonly faPenToSquare = faPenToSquare;
    protected readonly faXmark = faXmark;
    protected readonly LectureDraftState = LectureDraftState;
    protected readonly minimumStartDate = new Date();

    courseId = input.required<number>();
    lectureDrafts = signal<LectureDraft[]>([]);
    weekdayOptions = computed<WeekdayOption[]>(() => {
        this.currentLocale();
        return this.computeWeekdayOptions();
    });
    selectedWeekdayIndex = signal<WeekdayIndex | undefined>(undefined);
    startTime = signal<string | undefined>(undefined);
    endTime = signal<string | undefined>(undefined);
    isStartAndEndTimeCombinationInvalid = computed(() => this.isStartTimeSameOrAfterEndTime(this.startTime(), this.endTime()));
    startDate = signal<Date | undefined>(undefined);
    endDate = signal<Date | undefined>(undefined);
    minimumEndDate = computed(() => addOneMinuteTo(this.startDate()) ?? new Date());
    isEndDateInvalid = computed(() => isFirstAfterOrEqualSecond(this.startDate(), this.endDate()));
    isLoading = signal(false);
    areInputsInvalid = computed(() => this.isStartAndEndTimeCombinationInvalid() || this.isEndDateInvalid());

    constructor() {
        effect(() => this.updateLectureDrafts(this.areInputsInvalid(), this.selectedWeekdayIndex(), this.startTime(), this.endTime(), this.startDate(), this.endDate()));
    }

    onSelectedWeekdayOptionChange(optionWeekdayIndex: WeekdayIndex) {
        this.selectedWeekdayIndex.set(optionWeekdayIndex);
    }

    onStartTimeChange(time: string) {
        this.startTime.set(time);
    }

    onEndTimeChange(time: string) {
        this.endTime.set(time);
    }

    onStartDateChange(date: Date) {
        this.startDate.set(date);
    }

    onEndDateChange(date: Date) {
        this.endDate.set(date);
    }

    deleteLectureDraft(lectureDraft: LectureDraft) {
        lectureDraft.state = LectureDraftState.DELETED;
    }

    save() {
        this.isLoading.set(true);
        const lecturesToSave = this.lectureDrafts()
            .filter((d) => d.state !== LectureDraftState.DELETED)
            .map((d) => d.dto);
        const courseId = this.courseId();
        this.lectureService
            .createSeries(lecturesToSave, courseId)
            .pipe(finalize(() => this.isLoading.set(false)))
            .subscribe({
                next: () => this.router.navigate(['course-management', courseId, 'lectures']),
                error: () => this.alertService.addErrorAlert('artemisApp.lecture.createSeries.seriesCreationError'),
            });
    }

    cancel() {
        const courseId = this.courseId();
        this.navigationUtilService.navigateBack(['course-management', courseId, 'lectures']);
    }

    private computeWeekdayOptions(): WeekdayOption[] {
        return [
            { label: this.translateService.instant('global.weekdays.monday'), weekdayIndex: 1 },
            { label: this.translateService.instant('global.weekdays.tuesday'), weekdayIndex: 2 },
            { label: this.translateService.instant('global.weekdays.wednesday'), weekdayIndex: 3 },
            { label: this.translateService.instant('global.weekdays.thursday'), weekdayIndex: 4 },
            { label: this.translateService.instant('global.weekdays.friday'), weekdayIndex: 5 },
            { label: this.translateService.instant('global.weekdays.saturday'), weekdayIndex: 6 },
            { label: this.translateService.instant('global.weekdays.sunday'), weekdayIndex: 7 },
        ];
    }

    private isStartTimeSameOrAfterEndTime(startTime?: string, endTime?: string): boolean {
        if (startTime && endTime) {
            const [startHour, startMinute] = this.getHourAndMinute(startTime);
            const [endHour, endMinute] = this.getHourAndMinute(endTime);
            if (startHour > endHour || (startHour === endHour && startMinute >= endMinute)) {
                return true;
            }
        }
        return false;
    }

    private updateLectureDrafts(areInputsInvalid: boolean, selectedWeekdayIndex?: number, startTime?: string, endTime?: string, startDate?: Date, endDate?: Date) {
        if (areInputsInvalid || !selectedWeekdayIndex || !startTime || !endTime || !startDate || !endDate) return;

        const lectureDates = this.generateDatePairs(selectedWeekdayIndex, startTime, endTime, startDate, endDate);
        this.lectureDrafts.update((oldDrafts) => {
            const keyToOldDraftMap = new Map(oldDrafts.map((draft) => [draft.key, draft]));
            const newDrafts: LectureDraft[] = [];
            lectureDates.forEach(([startDate, endDate], index) => {
                const currentKey = startDate.toISOString();
                const existing = keyToOldDraftMap.get(currentKey);
                if (existing) {
                    if (existing.state !== LectureDraftState.EDITED) {
                        existing.dto.startDate = startDate;
                        existing.dto.endDate = endDate;
                        if (existing.state === LectureDraftState.DELETED) {
                            existing.state = LectureDraftState.REGULAR;
                        }
                    }
                    newDrafts.push(existing);
                    keyToOldDraftMap.delete(currentKey);
                } else {
                    newDrafts.push({
                        key: startDate.toISOString(),
                        state: LectureDraftState.REGULAR,
                        dto: new LectureCreateDTO(`Lecture ${index + 1}`, undefined, undefined, startDate, endDate),
                    });
                }
            });
            return newDrafts;
        });
    }

    private generateDatePairs(weekdayIndex: number, startTime: string, endTime: string, seriesStartDate: Date, seriesEndDate: Date): [Dayjs, Dayjs][] {
        const [startHour, startMinute] = this.getHourAndMinute(startTime);
        const [endHour, endMinute] = this.getHourAndMinute(endTime);
        const seriesStart = dayjs(seriesStartDate);
        const seriesEnd = dayjs(seriesEndDate).endOf('day');
        let firstStart = seriesStart.isoWeekday(weekdayIndex).hour(startHour).minute(startMinute).second(0).millisecond(0);
        if (firstStart.isBefore(seriesStart)) {
            firstStart = firstStart.add(1, 'week');
        }
        const pairs: [Dayjs, Dayjs][] = [];
        for (let currentStart = firstStart; !currentStart.isAfter(seriesEnd); currentStart = currentStart.add(1, 'week')) {
            const currentEnd = currentStart.hour(endHour).minute(endMinute).second(0).millisecond(0);
            pairs.push([currentStart, currentEnd]);
        }
        return pairs;
    }

    private getHourAndMinute(time: string): [number, number] {
        const [hh, mm] = time.split(':');
        return [parseInt(hh, 10), parseInt(mm, 10)];
    }
}
