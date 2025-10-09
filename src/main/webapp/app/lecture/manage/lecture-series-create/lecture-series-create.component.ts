import { Component, Signal, WritableSignal, computed, effect, inject, input, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPenToSquare, faTrash, faXmark } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import { FloatLabelModule } from 'primeng/floatlabel';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService } from 'primeng/api';
import { LectureSeriesDraftEditModalComponent } from 'app/lecture/manage/lecture-series-edit-modal/lecture-series-draft-edit-modal.component';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { Router } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import dayjs, { Dayjs } from 'dayjs/esm';
import { finalize } from 'rxjs';
import { Lecture, LectureNameUpdateDTO, LectureSeriesCreateLectureDTO } from 'app/lecture/shared/entities/lecture.model';
import { isFirstDateAfterOrEqualSecond } from 'app/shared/util/date.utils';

interface InitialLecture {
    id: string;
    startDate: WritableSignal<Date | undefined>;
    endDate: WritableSignal<Date | undefined>;
    isStartDateInvalid: Signal<boolean>;
    isEndDateInvalid: Signal<boolean>;
}

interface ExistingLecture {
    id: number;
    title?: string;
    startDate?: Dayjs;
    endDate?: Dayjs;
    state: ExistingLectureState;
}

enum ExistingLectureState {
    ORIGINAL = 'original',
    ADAPTED = 'adapted',
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
 * Additionally, the component manages:
 * - `ExistingLecture`s: represent the timing and tile of existing lectures.
 *
 * Based on InitialLectures and ExistingLectures the component creates:
 * - `LectureDraft`s: encapsulate title and timing for a single potential lecture. Can be edited by the user without affecting other drafts.
 *
 * For each InitialLecture, the component generates weekly drafts (one per week) until the series end date.
 * It assigns default titles in the format `Lecture ${index + 1}` by sorting the union of ExistingLectures and LectureDrafts by the earliest
 * available of `startDate` or `endDate`; items missing both dates are placed at the end.
 *
 * If ExistingLectures exist on save that follow the default title format, the component asks the user whether he would like to renumber them.
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
    providers: [ConfirmationService],
    templateUrl: './lecture-series-create.component.html',
    styleUrl: './lecture-series-create.component.scss',
})
export class LectureSeriesCreateComponent {
    private lectureService = inject(LectureService);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);
    private router = inject(Router);
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private confirmationService = inject(ConfirmationService);
    private existingLectures = computed<ExistingLecture[]>(() => this.computeExistingLectures());

    protected readonly faPenToSquare = faPenToSquare;
    protected readonly faXmark = faXmark;
    protected readonly faTrash = faTrash;
    protected readonly LectureDraftState = LectureDraftState;

    rawExistingLectures = input<Lecture[]>();
    courseId = input.required<number>();
    seriesEndDate = signal<Date | undefined>(undefined);
    isSeriesEndDateInvalid = computed<boolean>(() => this.computeIsSeriesEndDateInvalid());
    lectureDrafts = signal<LectureDraft[]>([]);
    noDraftsGenerated = computed(() => this.lectureDrafts().length === 0);
    initialLectures = signal<InitialLecture[]>([this.createInitialLecture()]);
    isLoading = signal(false);

    constructor() {
        effect(() => this.updateLectureDraftsAndExistingLecturesBasedOnSeriesEndDateAndInitialLectures());
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
        const adaptedExistingLectures = this.existingLectures().filter((lecture) => lecture.state === ExistingLectureState.ADAPTED);
        if (adaptedExistingLectures.length > 0) {
            this.confirmationService.confirm({
                header: this.translateService.instant('artemisApp.lecture.createSeries.updateNameConfirmation.header'),
                message: this.translateService.instant('artemisApp.lecture.createSeries.updateNameConfirmation.message'),
                acceptLabel: this.translateService.instant('global.generic.yes'),
                rejectLabel: this.translateService.instant('global.generic.no'),
                accept: () => this.updateNamesOfExistingLecturesAndSaveNewLectures(adaptedExistingLectures, courseId),
                reject: () => this.saveNewLectures(courseId),
            });
        } else {
            this.saveNewLectures(courseId);
        }
    }

    private updateNamesOfExistingLecturesAndSaveNewLectures(adaptedExistingLectures: ExistingLecture[], courseId: number) {
        const updateNameDTOs = adaptedExistingLectures.map((lecture) => new LectureNameUpdateDTO(lecture.id!, lecture.title!));
        this.lectureService.updateNames(updateNameDTOs, courseId).subscribe({
            next: () => this.saveNewLectures(courseId),
            error: () => {
                this.alertService.addErrorAlert('artemisApp.lecture.createSeries.updateNameError');
                this.isLoading.set(false);
            },
        });
    }

    private saveNewLectures(courseId: number) {
        const lecturesToSave = this.lectureDrafts().map((d) => d.dto);
        this.lectureService
            .createSeries(lecturesToSave, courseId)
            .pipe(finalize(() => this.isLoading.set(false)))
            .subscribe({
                next: () => this.router.navigate(['course-management', courseId, 'lectures']),
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.lecture.createSeries.seriesCreationError');
                    this.isLoading.set(false);
                },
            });
    }

    private updateLectureDraftsAndExistingLecturesBasedOnSeriesEndDateAndInitialLectures() {
        const lectureDrafts = this.computeLectureDraftsBasedOnSeriesEndDateAndInitialLectures();
        if (lectureDrafts === undefined) {
            return;
        }

        const sortedLectureDrafts = this.sort(lectureDrafts, (draft) => this.getSortingKeyFor(draft.dto));
        const sortedExistingLectures = this.sort(this.existingLectures(), (lecture) => this.getSortingKeyFor(lecture));
        this.assignTitlesToNewAndExistingLectures(sortedLectureDrafts, sortedExistingLectures);

        this.lectureDrafts.set(sortedLectureDrafts);
    }

    /* Helpers */

    private computeLectureDraftsBasedOnSeriesEndDateAndInitialLectures(): LectureDraft[] | undefined {
        const rawSeriesEndDate = this.seriesEndDate();
        if (!rawSeriesEndDate) {
            return undefined;
        }
        const endDate = dayjs(rawSeriesEndDate).endOf('day');
        let lectureDrafts: LectureDraft[] = [];
        for (const initialLecture of this.initialLectures()) {
            const lectureDraftsFromInitialLecture = this.computeLectureDraftsForInitialLecture(initialLecture, endDate);
            if (lectureDraftsFromInitialLecture === undefined) {
                return undefined;
            }
            lectureDrafts = [...lectureDrafts, ...lectureDraftsFromInitialLecture];
        }
        return lectureDrafts;
    }

    private computeLectureDraftsForInitialLecture(initialLecture: InitialLecture, seriesEndDate: Dayjs): LectureDraft[] | undefined {
        const startDate = initialLecture.startDate();
        const endDate = initialLecture.endDate();
        const isStartDateInvalid = initialLecture.isStartDateInvalid();
        const isEndDateInvalid = initialLecture.isEndDateInvalid();
        if ((!startDate && !endDate) || isStartDateInvalid || isEndDateInvalid) {
            return undefined;
        }

        const lectureDates = this.generateDatePairs(seriesEndDate, startDate, endDate);
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

    private getSortingKeyFor(lectureRepresentation: LectureSeriesCreateLectureDTO | ExistingLecture): number | undefined {
        const keyDate = lectureRepresentation.startDate ?? lectureRepresentation.endDate;
        if (keyDate) {
            return keyDate.valueOf();
        } else {
            return undefined;
        }
    }

    private assignTitlesToNewAndExistingLectures(sortedLectureDrafts: LectureDraft[], sortedExistingLectures: ExistingLecture[]) {
        const sortedDTOs = sortedLectureDrafts.map((draft) => draft.dto);
        const sortedLectureRepresentations = this.mergeSortedLectureRepresentations(sortedExistingLectures, sortedDTOs);
        sortedLectureRepresentations.forEach((lectureRepresentation, index) => {
            const standardNamePattern = /^Lecture \d+$/;
            if ((lectureRepresentation.title && lectureRepresentation.title.match(standardNamePattern)) || !lectureRepresentation.title) {
                lectureRepresentation.title = `Lecture ${index + 1}`;
                if ('state' in lectureRepresentation) {
                    lectureRepresentation.state = ExistingLectureState.ADAPTED;
                }
            }
        });
    }

    private mergeSortedLectureRepresentations(
        sortedExistingLectures: ExistingLecture[],
        sortedDTOs: LectureSeriesCreateLectureDTO[],
    ): (ExistingLecture | LectureSeriesCreateLectureDTO)[] {
        const sortedResult: (ExistingLecture | LectureSeriesCreateLectureDTO)[] = [];
        let existingLectureIndex = 0;
        let dtoIndex = 0;
        while (existingLectureIndex < sortedExistingLectures.length && dtoIndex < sortedDTOs.length) {
            const existingLecture = sortedExistingLectures[existingLectureIndex];
            const dto = sortedDTOs[dtoIndex];
            const existingLectureKey = this.getSortingKeyFor(existingLecture);
            const dtoKey = this.getSortingKeyFor(dto);
            if (existingLectureKey !== undefined && dtoKey !== undefined) {
                if (existingLectureKey <= dtoKey!) {
                    sortedResult.push(existingLecture);
                    existingLectureIndex++;
                } else {
                    sortedResult.push(dto);
                    dtoIndex++;
                }
            } else if (existingLectureKey !== undefined) {
                sortedResult.push(existingLecture);
                existingLectureIndex++;
            } else if (dtoKey !== undefined) {
                sortedResult.push(dto);
                dtoIndex++;
            } else {
                sortedResult.push(existingLecture);
                existingLectureIndex++;
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

    private computeExistingLectures(): ExistingLecture[] {
        const rawExistingLectures = this.rawExistingLectures() ?? [];
        return rawExistingLectures.map((lecture) => {
            return { id: lecture.id, title: lecture.title, startDate: lecture.startDate, endDate: lecture.endDate, state: ExistingLectureState.ORIGINAL } as ExistingLecture;
        });
    }
}
