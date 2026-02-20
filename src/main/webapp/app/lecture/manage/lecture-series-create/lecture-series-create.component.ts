import { Component, Signal, WritableSignal, computed, effect, inject, input, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPenToSquare, faPlus, faTrash, faXmark } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import { FloatLabelModule } from 'primeng/floatlabel';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { LectureSeriesDraftEditModalComponent } from 'app/lecture/manage/lecture-series-edit-modal/lecture-series-draft-edit-modal.component';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { Router } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import dayjs, { Dayjs } from 'dayjs/esm';
import { Lecture, LectureSeriesCreateLectureDTO } from 'app/lecture/shared/entities/lecture.model';
import { isFirstDateAfterOrEqualSecond } from 'app/shared/util/date.utils';

interface InitialLecture {
    id: string;
    startDate: WritableSignal<Date | undefined>;
    endDate: WritableSignal<Date | undefined>;
    isStartDateInvalid: Signal<boolean>;
    isEndDateInvalid: Signal<boolean>;
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

/**
 * A component for scaffolding lectures (creating lectures by specifying title and timing but no contents).
 *
 * Users define:
 * - `InitialLecture`s: encapsulate title, startDate and endDate of the *first* occurrence of a weekly lecture.
 * - `seriesEndDate`: the last day up to which weekly lectures should be generated.
 *
 * Based on initialLectures and existingLectures the component creates:
 * - `LectureDraft`s: encapsulate title and timing for a single potential lecture. Can be edited by the user without affecting other drafts.
 *
 * For each InitialLecture, the component generates weekly drafts (one per week) until the series end date.
 * It assigns default titles in the format `Lecture ${index + 1}` by sorting the union of existingLectures and lectureDrafts by the earliest
 * available of `startDate` or `endDate`; items missing both dates are placed at the end.
 *
 */
@Component({
    selector: 'jhi-lecture-series-create',
    imports: [
        NgClass,
        TranslateDirective,
        FormsModule,
        DatePickerModule,
        FloatLabelModule,
        ButtonModule,
        ConfirmDialogModule,
        FaIconComponent,
        LectureSeriesDraftEditModalComponent,
    ],
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
    protected readonly faTrash = faTrash;
    protected readonly faPlus = faPlus;
    protected readonly LectureDraftState = LectureDraftState;

    existingLectures = input.required<Lecture[]>();
    courseId = input.required<number>();
    seriesEndDate = signal<Date | undefined>(undefined);
    isSeriesEndDateInvalid = computed<boolean>(() => this.computeIsSeriesEndDateInvalid());
    lectureDrafts = signal<LectureDraft[]>([]);
    noDraftsGenerated = computed(() => this.lectureDrafts().length === 0);
    initialLectures = signal<InitialLecture[]>([this.createInitialLecture()]);
    isLoading = signal(false);

    constructor() {
        effect(() => this.updateLectureDraftsAndExistingLecturesBasedOnInitialLecturesAndSeriesEndDate());
    }

    addInitialLecture() {
        this.initialLectures.update((initialLectures) => [...initialLectures, this.createInitialLecture()]);
    }

    removeInitialLecture(initialLecture: InitialLecture) {
        this.initialLectures.update((initialLectures) => initialLectures.filter((otherInitialLecture) => otherInitialLecture.id !== initialLecture.id));
    }

    deleteLectureDraft(lectureDraft: LectureDraft) {
        this.lectureDrafts.update((oldDrafts) => oldDrafts.filter((otherDraft) => otherDraft !== lectureDraft));
    }

    cancel() {
        const courseId = this.courseId();
        this.navigationUtilService.navigateBack(['course-management', courseId, 'lectures']);
    }

    save() {
        this.isLoading.set(true);
        const courseId = this.courseId();
        this.saveNewLectures(courseId);
    }

    private saveNewLectures(courseId: number) {
        const lecturesToSave = this.lectureDrafts().map((d) => d.dto);
        this.lectureService.createSeries(lecturesToSave, courseId).subscribe({
            next: () => {
                this.router.navigate(['course-management', courseId, 'lectures']);
                this.isLoading.set(false);
            },
            error: () => {
                this.alertService.addErrorAlert('artemisApp.lecture.createSeries.seriesCreationError');
                this.isLoading.set(false);
            },
        });
    }

    private updateLectureDraftsAndExistingLecturesBasedOnInitialLecturesAndSeriesEndDate() {
        const lectureDrafts = this.computeLectureDraftsBasedOnSeriesEndDateAndInitialLectures();

        if (lectureDrafts.length === 0) {
            this.lectureDrafts.set([]);
            return;
        }

        const sortedLectureDrafts = this.sort(lectureDrafts, (draft) => this.getSortingKeyFor(draft.dto));
        const sortedExistingLectures = this.sort(this.existingLectures(), (lecture) => this.getSortingKeyFor(lecture));
        this.assignTitlesToNewLectures(sortedLectureDrafts, sortedExistingLectures);

        this.lectureDrafts.set(sortedLectureDrafts);
    }

    /* Helpers */

    private computeLectureDraftsBasedOnSeriesEndDateAndInitialLectures(): LectureDraft[] {
        const rawSeriesEndDate = this.seriesEndDate();
        const initialLectures = this.initialLectures();
        const anyInputInvalid = initialLectures.some((initialLecture) => initialLecture.isEndDateInvalid() || initialLecture.isStartDateInvalid()) || this.isSeriesEndDateInvalid();
        if (!rawSeriesEndDate || anyInputInvalid) {
            return [];
        }
        const endDate = dayjs(rawSeriesEndDate).endOf('day');
        let lectureDrafts: LectureDraft[] = [];
        for (const initialLecture of this.initialLectures()) {
            const lectureDraftsFromInitialLecture = this.computeLectureDraftsForInitialLecture(initialLecture, endDate);
            lectureDrafts = [...lectureDrafts, ...lectureDraftsFromInitialLecture];
        }
        return lectureDrafts;
    }

    private computeLectureDraftsForInitialLecture(initialLecture: InitialLecture, seriesEndDate: Dayjs): LectureDraft[] {
        const startDate = initialLecture.startDate();
        const endDate = initialLecture.endDate();
        if (!startDate && !endDate) {
            return [];
        }

        const lectureDates = this.generateDatePairs(seriesEndDate, startDate, endDate);
        const newDrafts: LectureDraft[] = [];
        lectureDates.forEach(([startDate, endDate]) => {
            newDrafts.push({
                id: window.crypto.randomUUID(),
                state: LectureDraftState.REGULAR,
                dto: new LectureSeriesCreateLectureDTO('', startDate, endDate),
            });
        });
        return newDrafts;
    }

    private generateDatePairs(seriesEndDate: Dayjs, startDate?: Date, endDate?: Date): [Dayjs?, Dayjs?][] {
        if (startDate && !endDate) {
            return this.generateDatePairsWithSingleDate(seriesEndDate, startDate, 'startDate');
        } else if (!startDate && endDate) {
            return this.generateDatePairsWithSingleDate(seriesEndDate, endDate, 'endDate');
        } else {
            return this.generateDatePairsWithBothDates(seriesEndDate, startDate!, endDate!);
        }
    }

    private generateDatePairsWithBothDates(seriesEndDate: Dayjs, startDate: Date, endDate: Date): [Dayjs, Dayjs][] {
        let currentStart = dayjs(startDate);
        let currentEnd = dayjs(endDate);
        const pairs: [Dayjs, Dayjs][] = [];
        while (!currentEnd.isAfter(seriesEndDate)) {
            pairs.push([currentStart, currentEnd]);
            currentStart = currentStart.add(1, 'week');
            currentEnd = currentEnd.add(1, 'week');
        }
        return pairs;
    }

    private generateDatePairsWithSingleDate(seriesEndDate: Dayjs, date: Date, dateType: 'startDate' | 'endDate'): [Dayjs?, Dayjs?][] {
        const pairs: [Dayjs?, Dayjs?][] = [];
        let current = dayjs(date);
        while (!current.isAfter(seriesEndDate)) {
            if (dateType === 'startDate') {
                pairs.push([current, undefined]);
            } else {
                pairs.push([undefined, current]);
            }
            current = current.add(1, 'week');
        }
        return pairs;
    }

    private sort<T>(items: T[], keyProvider: (item: T) => number | undefined): T[] {
        return [...items].sort((first, second) => {
            const firstKey = keyProvider(first);
            const secondKey = keyProvider(second);

            const firstHasKey = firstKey !== undefined;
            const secondHasKey = secondKey !== undefined;

            if (!firstHasKey && !secondHasKey) {
                return 0;
            }
            if (!firstHasKey) {
                return 1;
            }
            if (!secondHasKey) {
                return -1;
            }
            return firstKey - secondKey;
        });
    }

    private getSortingKeyFor(lectureRepresentation: LectureSeriesCreateLectureDTO | Lecture): number | undefined {
        const keyDate = lectureRepresentation.startDate ?? lectureRepresentation.endDate;
        if (keyDate) {
            return keyDate.valueOf();
        } else {
            return undefined;
        }
    }

    private assignTitlesToNewLectures(sortedLectureDrafts: LectureDraft[], sortedExistingLectures: Lecture[]) {
        const sortedDTOs = sortedLectureDrafts.map((draft) => draft.dto);
        const sortedLectureRepresentations = this.mergeSortedLectureRepresentations(sortedExistingLectures, sortedDTOs);
        sortedLectureRepresentations.forEach((lectureRepresentation, index) => {
            if (!lectureRepresentation.title) {
                lectureRepresentation.title = `Lecture ${index + 1}`;
            }
        });
    }

    private mergeSortedLectureRepresentations(sortedExistingLectures: Lecture[], sortedDTOs: LectureSeriesCreateLectureDTO[]): (Lecture | LectureSeriesCreateLectureDTO)[] {
        const sortedResult: (Lecture | LectureSeriesCreateLectureDTO)[] = [];
        let existingLectureIndex = 0;
        let dtoIndex = 0;
        while (existingLectureIndex < sortedExistingLectures.length && dtoIndex < sortedDTOs.length) {
            const dto = sortedDTOs[dtoIndex];
            const existingLecture = sortedExistingLectures[existingLectureIndex];
            const dtoKey = this.getSortingKeyFor(dto);
            const existingLectureKey = this.getSortingKeyFor(existingLecture);
            if (dtoKey !== undefined && existingLectureKey !== undefined) {
                if (existingLectureKey <= dtoKey) {
                    sortedResult.push(existingLecture);
                    existingLectureIndex++;
                } else {
                    sortedResult.push(dto);
                    dtoIndex++;
                }
            } else {
                // our validation logic enforces that there must be a key for all DTOs
                sortedResult.push(dto);
                dtoIndex++;
            }
        }
        while (existingLectureIndex < sortedExistingLectures.length) {
            sortedResult.push(sortedExistingLectures[existingLectureIndex++]);
        }
        while (dtoIndex < sortedDTOs.length) {
            sortedResult.push(sortedDTOs[dtoIndex++]);
        }
        return sortedResult;
    }

    private createInitialLecture(): InitialLecture {
        const id = window.crypto.randomUUID();
        const startDate = signal<Date | undefined>(undefined);
        const endDate = signal<Date | undefined>(undefined);
        const isStartDateInvalid = computed(() => isFirstDateAfterOrEqualSecond(startDate(), endDate()) || isFirstDateAfterOrEqualSecond(startDate(), this.seriesEndDate()));
        const isEndDateInvalid = computed(() => isFirstDateAfterOrEqualSecond(startDate(), endDate()) || isFirstDateAfterOrEqualSecond(endDate(), this.seriesEndDate()));
        return { id, startDate, endDate, isStartDateInvalid, isEndDateInvalid };
    }

    private computeIsSeriesEndDateInvalid(): boolean {
        const latestInitialLectureDate = this.initialLectures()
            .flatMap((lecture) => [lecture.startDate(), lecture.endDate()])
            .filter((date): date is Date => date !== undefined)
            .sort((first, second) => second.getTime() - first.getTime())[0];
        return isFirstDateAfterOrEqualSecond(latestInitialLectureDate, this.seriesEndDate());
    }
}
