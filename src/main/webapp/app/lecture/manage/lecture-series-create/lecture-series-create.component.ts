import { Component, Signal, WritableSignal, computed, effect, inject, input, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { FloatLabelModule } from 'primeng/floatlabel';
import { InputMaskModule } from 'primeng/inputmask';
import { ButtonModule } from 'primeng/button';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPenToSquare, faTrash, faXmark } from '@fortawesome/free-solid-svg-icons';
import dayjs, { Dayjs } from 'dayjs/esm';
import { LectureSeriesEditModalComponent } from 'app/lecture/manage/lecture-series-edit-modal/lecture-series-edit-modal.component';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { AlertService } from 'app/shared/service/alert.service';
import { Router } from '@angular/router';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { LectureSeriesCreateLectureDTO } from 'app/lecture/shared/entities/lecture.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { addOneMinuteTo, isFirstDateAfterOrEqualSecond } from 'app/shared/util/date.utils';
import { finalize } from 'rxjs';

type WeekdayIndex = 1 | 2 | 3 | 4 | 5 | 6 | 7;

interface WeekdayOption {
    label: string;
    weekdayIndex: WeekdayIndex;
}

export interface LectureDraft {
    id: string;
    state: LectureDraftState;
    dto: LectureSeriesCreateLectureDTO;
}

export enum LectureDraftState {
    EDITED = 'edited',
    REGULAR = 'regular',
}

interface WeeklyLecture {
    id: string;
    selectedWeekdayIndex: WritableSignal<WeekdayIndex | undefined>;
    startTimeString: WritableSignal<string | undefined>;
    endTimeString: WritableSignal<string | undefined>;
    startHourAndMinute: Signal<[number, number] | undefined>;
    endHourAndMinute: Signal<[number, number] | undefined>;
    isStartAndEndTimeCombinationInvalid: Signal<boolean>;
}

// TODO: check whether validation logic is in place (at least one date given for each lecture and endDate is after startDate)
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
    protected readonly faTrash = faTrash;
    protected readonly LectureDraftState = LectureDraftState;
    protected readonly minimumStartDate = new Date();

    courseId = input.required<number>();
    lectureDrafts = signal<LectureDraft[]>([]);
    isLoading = signal(false);
    noDraftsGenerated = computed(() => this.lectureDrafts().length === 0);
    seriesStartDate = signal<Date | undefined>(undefined);
    seriesEndDate = signal<Date | undefined>(undefined);
    minimumSeriesEndDate = computed(() => addOneMinuteTo(this.seriesStartDate()) ?? new Date());
    isSeriesEndDateInvalid = computed(() => isFirstDateAfterOrEqualSecond(this.seriesStartDate(), this.seriesEndDate()));
    weekdayOptions = computed<WeekdayOption[]>(() => this.computeWeekdayOptions());
    weeklyLectures = signal<WeeklyLecture[]>([this.createWeeklyLecture()]);

    constructor() {
        effect(() => this.updateLectureDrafts());
    }

    addWeeklyLecture() {
        this.weeklyLectures.update((weeklyLectures) => [...weeklyLectures, this.createWeeklyLecture()]);
    }

    removeWeeklyLecture(weeklyLecture: WeeklyLecture) {
        this.weeklyLectures.update((weeklyLectures) => weeklyLectures.filter((otherWeeklyLecture) => otherWeeklyLecture.id !== weeklyLecture.id));
    }

    deleteLectureDraft(lectureDraft: LectureDraft) {
        this.lectureDrafts.update((oldDrafts) => oldDrafts.filter((otherDraft) => otherDraft !== lectureDraft));
    }

    save() {
        this.isLoading.set(true);
        const lecturesToSave = this.lectureDrafts().map((d) => d.dto);
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

    private createWeeklyLecture(): WeeklyLecture {
        const id = window.crypto.randomUUID();
        const selectedWeekdayIndex = signal<WeekdayIndex | undefined>(undefined);
        const startTimeString = signal<string | undefined>(undefined);
        const endTimeString = signal<string | undefined>(undefined);
        const startHourAndMinute = computed<[number, number] | undefined>(() => this.getHourAndMinute(startTimeString()));
        const endHourAndMinute = computed<[number, number] | undefined>(() => this.getHourAndMinute(endTimeString()));
        const isStartAndEndTimeCombinationInvalid = computed(() => this.isStartTimeSameOrAfterEndTime(startHourAndMinute(), endHourAndMinute()));
        return { id, selectedWeekdayIndex, startTimeString, endTimeString, startHourAndMinute, endHourAndMinute, isStartAndEndTimeCombinationInvalid };
    }

    private getHourAndMinute(time?: string): [number, number] | undefined {
        if (!this.isCompleteTime(time)) {
            return undefined;
        }
        const [hh, mm] = time!.split(':');
        return [parseInt(hh, 10), parseInt(mm, 10)];
    }

    private isCompleteTime(t?: string): boolean {
        return t !== undefined && /^\d{2} : \d{2}$/.test(t);
    }

    private computeWeekdayOptions(): WeekdayOption[] {
        this.currentLocale();
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

    private isStartTimeSameOrAfterEndTime(startHourAndMinute?: [number, number], endHourAndMinute?: [number, number] | undefined): boolean {
        if (startHourAndMinute && endHourAndMinute) {
            const [startHour, startMinute] = startHourAndMinute;
            const [endHour, endMinute] = endHourAndMinute;
            if (startHour > endHour || (startHour === endHour && startMinute >= endMinute)) {
                return true;
            }
        }
        return false;
    }

    private updateLectureDrafts() {
        const rawSeriesStartDate = this.seriesStartDate();
        const rawSeriesEndDate = this.seriesEndDate();
        if (!rawSeriesStartDate || !rawSeriesEndDate) {
            return;
        }
        const startDate = dayjs(rawSeriesStartDate);
        const endDate = dayjs(rawSeriesEndDate).endOf('day');

        let lectureDrafts: LectureDraft[] = [];
        for (const weeklyLecture of this.weeklyLectures()) {
            const weeklyLectureDrafts = this.computeLectureDraftsForWeeklyLecture(weeklyLecture, startDate, endDate);
            if (weeklyLectureDrafts === undefined) {
                return;
            }
            lectureDrafts = [...lectureDrafts, ...weeklyLectureDrafts];
        }
        lectureDrafts.sort((first, second) => this.getSortingKeyFor(first) - this.getSortingKeyFor(second)).forEach((draft, index) => (draft.dto.title = `Lecture ${index + 1}`));
        this.lectureDrafts.set(lectureDrafts);
    }

    private getSortingKeyFor(lectureDraft: LectureDraft): number {
        const keyDate = lectureDraft.dto.startDate ?? lectureDraft.dto.endDate!;
        return keyDate.valueOf();
    }

    private computeLectureDraftsForWeeklyLecture(weeklyLecture: WeeklyLecture, startDate: Dayjs, endDate: Dayjs): LectureDraft[] | undefined {
        const isStartAndEndTimeCombinationInvalid = weeklyLecture.isStartAndEndTimeCombinationInvalid();
        const selectedWeekdayIndex = weeklyLecture.selectedWeekdayIndex();
        const startHourAndMinute = weeklyLecture.startHourAndMinute();
        const endHourAndMinute = weeklyLecture.endHourAndMinute();
        if (!selectedWeekdayIndex || isStartAndEndTimeCombinationInvalid || (!startHourAndMinute && !endHourAndMinute)) {
            return undefined;
        }
        const lectureDates = this.generateDatePairs(selectedWeekdayIndex, startDate, endDate, startHourAndMinute, endHourAndMinute);
        const newDrafts: LectureDraft[] = [];
        lectureDates.forEach(([startDate, endDate], index) => {
            newDrafts.push({
                id: window.crypto.randomUUID(),
                state: LectureDraftState.REGULAR,
                dto: new LectureSeriesCreateLectureDTO('', startDate, endDate),
            });
        });
        return newDrafts;
    }

    private generateDatePairs(
        weekdayIndex: number,
        seriesStartDate: Dayjs,
        seriesEndDate: Dayjs,
        startHourAndMinute?: [number, number],
        endHourAndMinute?: [number, number],
    ): [Dayjs?, Dayjs?][] {
        if (startHourAndMinute && !endHourAndMinute) {
            return this.generateDatePairsWithOnlyStartDate(weekdayIndex, seriesStartDate, seriesEndDate, startHourAndMinute);
        } else if (!startHourAndMinute && endHourAndMinute) {
            return this.generateDatePairsWithOnlyEndDate(weekdayIndex, seriesStartDate, seriesEndDate, endHourAndMinute);
        }
        const [startHour, startMinute] = startHourAndMinute!;
        const [endHour, endMinute] = endHourAndMinute!;
        let firstStart = seriesStartDate.isoWeekday(weekdayIndex).hour(startHour).minute(startMinute).second(0).millisecond(0);
        if (firstStart.isBefore(seriesStartDate)) {
            firstStart = firstStart.add(1, 'week');
        }
        const pairs: [Dayjs, Dayjs][] = [];
        for (let currentStart = firstStart; !currentStart.isAfter(seriesEndDate); currentStart = currentStart.add(1, 'week')) {
            const currentEnd = currentStart.hour(endHour).minute(endMinute).second(0).millisecond(0);
            pairs.push([currentStart, currentEnd]);
        }
        return pairs;
    }

    private generateDatePairsWithOnlyStartDate(weekdayIndex: number, seriesStartDate: Dayjs, seriesEndDate: Dayjs, startHourAndMinute: [number, number]): [Dayjs, undefined][] {
        const [startHour, startMinute] = startHourAndMinute;
        let firstStart = seriesStartDate.isoWeekday(weekdayIndex).hour(startHour).minute(startMinute).second(0).millisecond(0);
        if (firstStart.isBefore(seriesStartDate)) {
            firstStart = firstStart.add(1, 'week');
        }
        const pairs: [Dayjs, undefined][] = [];
        for (let currentStart = firstStart; !currentStart.isAfter(seriesEndDate); currentStart = currentStart.add(1, 'week')) {
            pairs.push([currentStart, undefined]);
        }
        return pairs;
    }

    private generateDatePairsWithOnlyEndDate(weekdayIndex: number, seriesStartDate: Dayjs, seriesEndDate: Dayjs, endHourAndMinute: [number, number]): [undefined, Dayjs][] {
        const [endHour, endMinute] = endHourAndMinute;
        let firstEnd = seriesStartDate.isoWeekday(weekdayIndex).hour(endHour).minute(endMinute).second(0).millisecond(0);
        if (firstEnd.isBefore(seriesStartDate)) {
            firstEnd = firstEnd.add(1, 'week');
        }
        const pairs: [undefined, Dayjs][] = [];
        for (let currentEnd = firstEnd; !currentEnd.isAfter(seriesEndDate); currentEnd = currentEnd.add(1, 'week')) {
            pairs.push([undefined, currentEnd]);
        }
        return pairs;
    }
}
