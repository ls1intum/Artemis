import { PROFILE_IRIS } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import dayjs from 'dayjs/esm';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { filter, map } from 'rxjs/operators';
import { LectureService } from '../services/lecture.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { faFile, faFileExport, faFileImport, faFilter, faPencilAlt, faPlus, faPuzzlePiece, faSort, faTrash } from '@fortawesome/free-solid-svg-icons';
import { LectureImportComponent } from 'app/lecture/manage/lecture-import/lecture-import.component';
import { Subject, Subscription } from 'rxjs';
import { DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { SortService } from 'app/shared/service/sort.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { IngestionState } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { CourseTitleBarTitleComponent } from 'app/core/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarTitleDirective } from 'app/core/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarActionsDirective } from 'app/core/course/shared/directives/course-title-bar-actions.directive';

export enum LectureDateFilter {
    PAST = 'filterPast',
    CURRENT = 'filterCurrent',
    FUTURE = 'filterFuture',
    UNSPECIFIED = 'filterUnspecifiedDates',
}

@Component({
    selector: 'jhi-lecture',
    templateUrl: './lecture.component.html',
    imports: [
        TranslateDirective,
        DocumentationButtonComponent,
        NgbDropdown,
        NgbDropdownToggle,
        NgClass,
        FaIconComponent,
        NgbDropdownMenu,
        RouterLink,
        SortDirective,
        SortByDirective,
        DeleteButtonDirective,
        ArtemisDatePipe,
        HtmlForMarkdownPipe,
        CourseTitleBarTitleComponent,
        CourseTitleBarTitleDirective,
        CourseTitleBarActionsDirective,
    ],
})
export class LectureComponent implements OnInit, OnDestroy {
    protected lectureService = inject(LectureService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private sortService = inject(SortService);
    private profileService = inject(ProfileService);
    private irisSettingsService = inject(IrisSettingsService);

    lectures: Lecture[];
    filteredLectures: Lecture[];
    courseId: number;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    activeFilters = new Set<LectureDateFilter>();
    predicate: string;
    ascending: boolean;

    irisEnabled = false;

    readonly filterType = LectureDateFilter;
    readonly documentationType: DocumentationType = 'Lecture';
    readonly ingestionState: IngestionState;

    // Icons
    faPlus = faPlus;
    faFileImport = faFileImport;
    faFileExport = faFileExport;
    faTrash = faTrash;
    faPencilAlt = faPencilAlt;
    faFile = faFile;
    faPuzzlePiece = faPuzzlePiece;
    faFilter = faFilter;
    faSort = faSort;
    lectureIngestionEnabled = false;

    protected readonly IngestionState = IngestionState;

    private profileInfoSubscription: Subscription;

    constructor() {
        this.predicate = 'id';
        this.ascending = true;
    }

    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.irisEnabled = this.profileService.isProfileActive(PROFILE_IRIS);
        if (this.irisEnabled) {
            this.irisSettingsService.getCourseSettings(this.courseId).subscribe((response) => {
                this.lectureIngestionEnabled = response?.settings?.enabled || false;
                if (this.lectureIngestionEnabled && this.lectures?.length) {
                    this.updateIngestionStates();
                }
            });
        }

        this.loadAll();
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
        this.profileInfoSubscription?.unsubscribe();
    }

    trackId(_index: number, item: Lecture) {
        return item.id;
    }

    /**
     * Opens the import modal and imports the selected lecture
     */
    openImportModal() {
        const modalRef = this.modalService.open(LectureImportComponent, { size: 'lg', backdrop: 'static' });
        modalRef.result.then(
            (result: Lecture) => {
                this.lectureService
                    .import(this.courseId, result.id!)
                    .pipe(
                        filter((res: HttpResponse<Lecture>) => res.ok),
                        map((res: HttpResponse<Lecture>) => res.body),
                    )
                    .subscribe({
                        next: (res: Lecture) => {
                            this.lectures.push(res);
                            this.router.navigate(['course-management', res.course!.id, 'lectures', res.id]);
                        },
                        error: (res: HttpErrorResponse) => onError(this.alertService, res),
                    });
            },
            () => {},
        );
    }

    private deleteLectureFromDisplayedLectures(lectureId: number) {
        this.dialogErrorSource.next('');
        this.lectures = this.lectures.filter((lecture) => lecture.id !== lectureId);
        this.applyFilters();
    }

    /**
     * Deletes Lecture
     * @param lectureId the id of the lecture
     */
    deleteLecture(lectureId: number) {
        this.lectureService.delete(lectureId).subscribe({
            next: () => {
                this.deleteLectureFromDisplayedLectures(lectureId);
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Toggles some filters for the lectures
     * @param filters The filters which should be toggled (activated if not already activated, and vice versa)
     */
    toggleFilters(filters: LectureDateFilter[]) {
        filters.forEach((f) => (this.activeFilters.has(f) ? this.activeFilters.delete(f) : this.activeFilters.add(f)));
        this.applyFilters();
    }

    sortRows() {
        this.sortService.sortByProperty(this.filteredLectures, this.predicate, this.ascending);
    }

    private loadAll() {
        this.lectureService
            .findAllByCourseId(this.courseId)
            .pipe(
                filter((res: HttpResponse<Lecture[]>) => res.ok),
                map((res: HttpResponse<Lecture[]>) => res.body),
            )
            .subscribe({
                next: (res: Lecture[]) => {
                    this.lectures = res.map((lectureData) => {
                        const lecture = new Lecture();
                        Object.assign(lecture, lectureData);
                        return lecture;
                    });
                    if (this.lectureIngestionEnabled) {
                        this.updateIngestionStates();
                    }
                    this.applyFilters();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    /**
     * Updates the lectures to show by applying the filters and sorting them
     */
    private applyFilters(): void {
        if (this.activeFilters.size === 0) {
            // If no filters selected, show all lectures
            this.filteredLectures = this.lectures;
        } else {
            // Get the current system time
            const now = dayjs();
            // Initialize empty arrays for filtered Lectures
            let filteredLectures: Array<Lecture> = [];

            // update filteredLectures based on the selected filter option checkboxes
            const pastLectures = this.lectures.filter((lecture) => lecture.endDate?.isBefore(now));
            const currentLectures = this.lectures.filter((lecture) => {
                if (lecture.startDate && lecture.endDate) {
                    return lecture.startDate.isSameOrBefore(now) && lecture.endDate.isSameOrAfter(now);
                } else if (lecture.startDate) {
                    return lecture.startDate.isSameOrBefore(now);
                } else if (lecture.endDate) {
                    return lecture.endDate.isSameOrAfter(now);
                }
                return false;
            });
            const futureLectures = this.lectures.filter((lecture) => lecture.startDate?.isAfter(now));
            const unspecifiedDatesLectures = this.lectures.filter((lecture) => lecture.startDate === undefined && lecture.endDate === undefined);

            filteredLectures = this.activeFilters.has(LectureDateFilter.PAST) ? filteredLectures.concat(pastLectures) : filteredLectures;
            filteredLectures = this.activeFilters.has(LectureDateFilter.CURRENT) ? filteredLectures.concat(currentLectures) : filteredLectures;
            filteredLectures = this.activeFilters.has(LectureDateFilter.FUTURE) ? filteredLectures.concat(futureLectures) : filteredLectures;
            filteredLectures = this.activeFilters.has(LectureDateFilter.UNSPECIFIED) ? filteredLectures.concat(unspecifiedDatesLectures) : filteredLectures;
            this.filteredLectures = filteredLectures;
        }

        this.sortRows();
    }

    /**
     * Trigger the Ingestion of all Lectures in the course.
     */
    ingestLecturesInPyris() {
        if (this.lectures.first()) {
            this.lectureService.ingestLecturesInPyris(this.lectures.first()!.course!.id!).subscribe({
                next: () => this.alertService.success('artemisApp.iris.ingestionAlert.allLecturesSuccess'),
                error: () => {
                    this.alertService.error('artemisApp.iris.ingestionAlert.allLecturesError');
                },
            });
        }
    }

    /**
     * Fetches the ingestion state for all lecture asynchronously and updates all the lectures ingestion state.
     */
    updateIngestionStates() {
        this.lectureService.getIngestionState(this.courseId).subscribe({
            next: (res: HttpResponse<Record<number, IngestionState>>) => {
                if (res.body) {
                    const ingestionStatesMap = res.body;
                    this.lectures.forEach((lecture) => {
                        if (lecture.id) {
                            const ingestionState = ingestionStatesMap[lecture.id];
                            if (ingestionState !== undefined) {
                                lecture.ingested = ingestionState;
                            }
                        }
                    });
                }
            },
            error: () => {
                this.alertService.error('artemisApp.iris.ingestionAlert.pyrisError');
            },
        });
    }

    navigateToLectureCreationPage(): void {
        this.router.navigate(['course-management', this.courseId, 'lectures', 'new'], {
            state: { existingLectures: this.lectures },
        });
    }
}
