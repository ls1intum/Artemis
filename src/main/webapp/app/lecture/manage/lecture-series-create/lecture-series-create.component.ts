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
import { LectureNameUpdateDTO, LectureSeriesCreateLectureDTO } from 'app/lecture/shared/entities/lecture.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { isFirstDateAfterOrEqualSecond } from 'app/shared/util/date.utils';
import { finalize } from 'rxjs';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService } from 'primeng/api';
import { TranslateService } from '@ngx-translate/core';

export interface LectureDraft {
    id: string;
    state: LectureDraftState;
    dto: LectureSeriesCreateLectureDTO;
}

export enum LectureDraftState {
    EDITED = 'edited',
    REGULAR = 'regular',
}

interface FirstLecture {
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
    ORIGINAL = 'ORIGINAL',
    ADAPTED = 'ADAPTED',
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
        ConfirmDialogModule,
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
    private courseStorageService = inject(CourseStorageService);
    private existingLectures: ExistingLecture[] = [];

    protected readonly faPenToSquare = faPenToSquare;
    protected readonly faXmark = faXmark;
    protected readonly faTrash = faTrash;
    protected readonly LectureDraftState = LectureDraftState;

    courseId = input.required<number>();
    lectureDrafts = signal<LectureDraft[]>([]);
    isLoading = signal(false);
    noDraftsGenerated = computed(() => this.lectureDrafts().length === 0);
    seriesEndDate = signal<Date | undefined>(undefined);
    seriesEndDateInvalid = computed<boolean>(() => this.computeSeriesEndDateInvalid());
    firstLectures = signal<FirstLecture[]>([this.createFirstLecture()]);

    constructor() {
        effect(() => this.updateLectureDraftsAndExistingLectures());
        effect(() => {
            const existingLectures = this.courseStorageService.getCourse(this.courseId())?.lectures ?? [];
            this.existingLectures = existingLectures.map((lecture) => {
                return { id: lecture.id, title: lecture.title, startDate: lecture.startDate, endDate: lecture.endDate, state: ExistingLectureState.ORIGINAL } as ExistingLecture;
            });
        });
    }

    addFirstLecture() {
        this.firstLectures.update((firstLectures) => [...firstLectures, this.createFirstLecture()]);
    }

    removeFirstLecture(firstLecture: FirstLecture) {
        this.firstLectures.update((firstLectures) => firstLectures.filter((otherFirstLecture) => otherFirstLecture.id !== firstLecture.id));
    }

    deleteLectureDraft(lectureDraft: LectureDraft) {
        this.lectureDrafts.update((oldDrafts) => oldDrafts.filter((otherDraft) => otherDraft !== lectureDraft));
    }

    save() {
        this.isLoading.set(true);
        const courseId = this.courseId();
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.lecture.createSeries.updateNameConfirmation.header'),
            message: this.translateService.instant('artemisApp.lecture.createSeries.updateNameConfirmation.message'),
            acceptLabel: this.translateService.instant('global.generic.yes'),
            rejectLabel: this.translateService.instant('global.generic.no'),
            accept: () => this.updateNamesOfExistingLecturesAndSaveNewLectures(courseId),
            reject: () => this.saveNewLectures(courseId),
        });
    }

    private updateNamesOfExistingLecturesAndSaveNewLectures(courseId: number) {
        const updateNameDTOs = this.existingLectures
            .filter((lecture) => lecture.state === ExistingLectureState.ADAPTED)
            .map((lecture) => new LectureNameUpdateDTO(lecture.id!, lecture.title!));
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

    cancel() {
        const courseId = this.courseId();
        this.navigationUtilService.navigateBack(['course-management', courseId, 'lectures']);
    }

    private createFirstLecture(): FirstLecture {
        const id = window.crypto.randomUUID();
        const startDate = signal<Date | undefined>(undefined);
        const endDate = signal<Date | undefined>(undefined);
        const isStartDateInvalid = computed(() => isFirstDateAfterOrEqualSecond(startDate(), endDate()) || isFirstDateAfterOrEqualSecond(startDate(), this.seriesEndDate()));
        const isEndDateInvalid = computed(() => isFirstDateAfterOrEqualSecond(startDate(), endDate()) || isFirstDateAfterOrEqualSecond(endDate(), this.seriesEndDate()));
        return { id, startDate, endDate, isStartDateInvalid, isEndDateInvalid };
    }

    private updateLectureDraftsAndExistingLectures() {
        const rawSeriesEndDate = this.seriesEndDate();
        if (!rawSeriesEndDate) {
            return;
        }
        const endDate = dayjs(rawSeriesEndDate).endOf('day');

        let lectureDrafts: LectureDraft[] = [];
        for (const firstLecture of this.firstLectures()) {
            const lectureDraftsFromFirstLecture = this.computeLectureDraftsForFirstLecture(firstLecture, endDate);
            if (lectureDraftsFromFirstLecture === undefined) {
                return;
            }
            lectureDrafts = [...lectureDrafts, ...lectureDraftsFromFirstLecture];
        }
        const sortedLectureDrafts = this.sortByOptionalKey(lectureDrafts, (draft) => this.getSortingKeyFor(draft.dto));

        const sortedExistingLectures = this.sortByOptionalKey(this.existingLectures, (lecture) => this.getSortingKeyFor(lecture));
        const newDtos = sortedLectureDrafts.map((draft) => draft.dto);
        const sortedLectureRepresentations = this.mergeLectureRepresentations(sortedExistingLectures, newDtos);
        sortedLectureRepresentations.forEach((lectureRepresentation, index) => {
            const standardNamePattern = /^Lecture \d+$/;
            if ((lectureRepresentation.title && lectureRepresentation.title.match(standardNamePattern)) || !lectureRepresentation.title) {
                lectureRepresentation.title = `Lecture ${index + 1}`;
                if ('state' in lectureRepresentation) {
                    lectureRepresentation.state = ExistingLectureState.ADAPTED;
                }
            }
        });

        this.lectureDrafts.set(sortedLectureDrafts);
    }

    sortByOptionalKey<T>(items: T[], getKey: (item: T) => number | undefined): T[] {
        return [...items].sort((first, second) => {
            const firstKey = getKey(first);
            const secondKey = getKey(second);

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

    private mergeLectureRepresentations(existingLectures: ExistingLecture[], dtos: LectureSeriesCreateLectureDTO[]): (ExistingLecture | LectureSeriesCreateLectureDTO)[] {
        const result: (ExistingLecture | LectureSeriesCreateLectureDTO)[] = [];
        let existingLectureIndex = 0;
        let dtoIndex = 0;
        while (existingLectureIndex < existingLectures.length && dtoIndex < dtos.length) {
            const existingLecture = existingLectures[existingLectureIndex];
            const dto = dtos[dtoIndex];
            const existingLectureKey = this.getSortingKeyFor(existingLecture);
            const dtoKey = this.getSortingKeyFor(dto);
            if (existingLectureKey !== undefined && dtoKey !== undefined) {
                if (existingLectureKey <= dtoKey!) {
                    result.push(existingLecture);
                    existingLectureIndex++;
                } else {
                    result.push(dto);
                    dtoIndex++;
                }
            } else if (existingLectureKey !== undefined) {
                result.push(existingLecture);
                existingLectureIndex++;
            } else if (dtoKey !== undefined) {
                result.push(dto);
                dtoIndex++;
            } else {
                result.push(existingLecture);
                existingLectureIndex++;
            }
        }
        while (existingLectureIndex < existingLectures.length) {
            result.push(existingLectures[existingLectureIndex++]);
        }
        while (dtoIndex < dtos.length) {
            result.push(dtos[dtoIndex++]);
        }
        return result;
    }

    private getSortingKeyFor(lectureRepresentation: LectureSeriesCreateLectureDTO | ExistingLecture): number | undefined {
        const keyDate = lectureRepresentation.startDate ?? lectureRepresentation.endDate;
        if (keyDate) {
            return keyDate.valueOf();
        } else {
            return undefined;
        }
    }

    private computeLectureDraftsForFirstLecture(firstLecture: FirstLecture, seriesEndDate: Dayjs): LectureDraft[] | undefined {
        const startDate = firstLecture.startDate();
        const endDate = firstLecture.endDate();
        const isStartDateInvalid = firstLecture.isStartDateInvalid();
        const isEndDateInvalid = firstLecture.isEndDateInvalid();
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
            return this.generateDatePairsWithOnlyStartDate(seriesEndDate, startDate);
        } else if (!startDate && endDate) {
            return this.generateDatePairsWithOnlyEndDate(seriesEndDate, endDate);
        }
        let currentStart = dayjs(startDate!);
        let currentEnd = dayjs(endDate!);
        const pairs: [Dayjs, Dayjs][] = [];
        while (!currentEnd.isAfter(seriesEndDate)) {
            pairs.push([currentStart, currentEnd]);
            currentStart = currentStart.add(1, 'week');
            currentEnd = currentEnd.add(1, 'week');
        }
        return pairs;
    }

    private generateDatePairsWithOnlyStartDate(seriesEndDate: Dayjs, startDate: Date): [Dayjs, undefined][] {
        let currentStart = dayjs(startDate!);
        const pairs: [Dayjs, undefined][] = [];
        while (!currentStart.isAfter(seriesEndDate)) {
            pairs.push([currentStart, undefined]);
            currentStart = currentStart.add(1, 'week');
        }
        return pairs;
    }
    private generateDatePairsWithOnlyEndDate(seriesEndDate: Dayjs, endDate: Date): [undefined, Dayjs][] {
        let currentEnd = dayjs(endDate!);
        const pairs: [undefined, Dayjs][] = [];
        while (!currentEnd.isAfter(seriesEndDate)) {
            pairs.push([undefined, currentEnd]);
            currentEnd = currentEnd.add(1, 'week');
        }
        return pairs;
    }

    private computeSeriesEndDateInvalid(): boolean {
        const latestFirstLectureDate = this.firstLectures()
            .flatMap((lecture) => [lecture.startDate(), lecture.endDate()])
            .filter((date): date is Date => date !== undefined)
            .sort((first, second) => first.getTime() - second.getTime())[0];
        return isFirstDateAfterOrEqualSecond(latestFirstLectureDate ?? new Date(), this.seriesEndDate());
    }
}
