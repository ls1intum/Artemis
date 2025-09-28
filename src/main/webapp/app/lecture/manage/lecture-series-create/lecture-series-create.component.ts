import { Component, effect, inject, input, signal } from '@angular/core';
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

// TODO: create strings
// TODO: adapt lecture numbering to existing lectures
// TODO: add loading indicator while saving
// TODO: add input validation
@Component({
    selector: 'jhi-lecture-series-create',
    imports: [SelectModule, FormsModule, DatePickerModule, FloatLabelModule, InputMaskModule, ButtonModule, FaIconComponent, LectureSeriesEditModalComponent, NgClass],
    templateUrl: './lecture-series-create.component.html',
    styleUrl: './lecture-series-create.component.scss',
})
export class LectureSeriesCreateComponent {
    private lectureService = inject(LectureService);
    private alertService = inject(AlertService);
    private router = inject(Router);
    private navigationUtilService = inject(ArtemisNavigationUtilService);

    protected readonly faPenToSquare = faPenToSquare;
    protected readonly faXmark = faXmark;
    protected readonly LectureDraftState = LectureDraftState;

    courseId = input.required<number>();
    lectureDrafts = signal<LectureDraft[]>([]);
    weekdayOptions: WeekdayOption[] = [
        { label: 'Monday', weekdayIndex: 1 },
        { label: 'Tuesday', weekdayIndex: 2 },
        { label: 'Wednesday', weekdayIndex: 3 },
        { label: 'Thursday', weekdayIndex: 4 },
        { label: 'Friday', weekdayIndex: 5 },
        { label: 'Saturday', weekdayIndex: 6 },
        { label: 'Sunday', weekdayIndex: 7 },
    ];
    selectedWeekdayIndex = signal<WeekdayIndex | undefined>(undefined);
    startTime = signal<string | undefined>(undefined);
    endTime = signal<string | undefined>(undefined);
    endDate = signal<Date | undefined>(undefined);

    constructor() {
        effect(() => this.updateLectureDrafts(this.selectedWeekdayIndex(), this.startTime(), this.endTime(), this.endDate()));
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

    deleteLectureDraft(lectureDraft: LectureDraft) {
        lectureDraft.state = LectureDraftState.DELETED;
    }

    save() {
        const lecturesToSave = this.lectureDrafts()
            .filter((draft) => draft.state !== LectureDraftState.DELETED)
            .map((draft) => draft.dto);
        const courseId = this.courseId();
        this.lectureService.createSeries(lecturesToSave, courseId).subscribe({
            next: () => {
                this.router.navigate(['course-management', courseId, 'lectures']);
            },
            error: () => {
                this.alertService.addErrorAlert('Something went wrong. Please try again.');
            },
        });
    }

    cancel() {
        const courseId = this.courseId();
        this.navigationUtilService.navigateBack(['course-management', courseId, 'lectures']);
    }

    private updateLectureDrafts(selectedWeekdayIndex?: number, startTime?: string, endTime?: string, endDate?: Date) {
        if (!selectedWeekdayIndex || !startTime || !endTime || !endDate) return;

        const lectureDates = this.generateDatePairsFromToday(selectedWeekdayIndex, startTime, endTime, endDate);
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

    private generateDatePairsFromToday(weekdayIndex: number, startTime: string, endTime: string, seriesEndDate: Date): [Dayjs, Dayjs][] {
        const [startHour, startMinute] = this.getHourAndMinute(startTime);
        const [endHour, endMinute] = this.getHourAndMinute(endTime);
        const seriesStart = dayjs();
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
