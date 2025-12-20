import dayjs from 'dayjs/esm';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { concatMap, filter, last, map } from 'rxjs/operators';
import { LectureService } from '../services/lecture.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { faFile, faFileImport, faFilter, faPencilAlt, faPlus, faPuzzlePiece, faSort, faTrash } from '@fortawesome/free-solid-svg-icons';
import { LectureImportComponent } from 'app/lecture/manage/lecture-import/lecture-import.component';
import { Subject, from } from 'rxjs';
import { DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { SortService } from 'app/shared/service/sort.service';
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
import { PdfDropZoneComponent } from '../pdf-drop-zone/pdf-drop-zone.component';
import { PdfUploadTarget, PdfUploadTargetDialogComponent } from '../pdf-upload-target-dialog/pdf-upload-target-dialog.component';
import { AttachmentVideoUnitService } from '../lecture-units/services/attachment-video-unit.service';

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
        PdfDropZoneComponent,
    ],
})
export class LectureComponent implements OnInit, OnDestroy {
    private lectureService = inject(LectureService);
    private attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private sortService = inject(SortService);

    lectures: Lecture[];
    isUploadingPdfs = signal(false);
    filteredLectures: Lecture[];
    courseId: number;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    activeFilters = new Set<LectureDateFilter>();
    predicate = 'id';
    ascending = true;

    readonly filterType = LectureDateFilter;
    readonly documentationType: DocumentationType = 'Lecture';

    // Icons
    faPlus = faPlus;
    faFileImport = faFileImport;
    faTrash = faTrash;
    faPencilAlt = faPencilAlt;
    faFile = faFile;
    faPuzzlePiece = faPuzzlePiece;
    faFilter = faFilter;
    faSort = faSort;

    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.loadAll();
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
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

    navigateToLectureCreationPage(): void {
        this.router.navigate(['course-management', this.courseId, 'lectures', 'new'], {
            state: { existingLectures: this.lectures },
        });
    }

    /**
     * Handles PDF files dropped on the drop zone
     * Opens a dialog to select target lecture (new or existing)
     */
    onPdfFilesDropped(files: File[]): void {
        const modalRef = this.modalService.open(PdfUploadTargetDialogComponent, {
            size: 'lg',
            backdrop: 'static',
        });
        modalRef.componentInstance.lectures = this.lectures;
        modalRef.componentInstance.initializeWithFiles(files);

        modalRef.result.then(
            (result: PdfUploadTarget) => {
                if (result.targetType === 'new' && result.newLectureTitle) {
                    this.createLectureWithUnits(result.newLectureTitle, files);
                } else if (result.targetType === 'existing' && result.lectureId) {
                    this.createUnitsForExistingLecture(result.lectureId, files);
                }
            },
            () => {
                // Dialog dismissed, do nothing
            },
        );
    }

    /**
     * Creates a new lecture with the given title and then creates attachment units for all files
     */
    private createLectureWithUnits(title: string, files: File[]): void {
        this.isUploadingPdfs.set(true);

        const lecture = new Lecture();
        lecture.title = title;
        lecture.course = new Course();
        lecture.course.id = this.courseId;

        this.lectureService
            .create(lecture)
            .pipe(
                filter((res: HttpResponse<Lecture>) => res.ok),
                map((res: HttpResponse<Lecture>) => res.body!),
                concatMap((createdLecture: Lecture) => {
                    // Add the new lecture to the list
                    this.lectures.push(createdLecture);
                    this.applyFilters();

                    // Create attachment units sequentially to maintain order
                    return from(files).pipe(
                        concatMap((file) => this.createAttachmentUnit(createdLecture.id!, file)),
                        map(() => createdLecture),
                        // Emit only the final value after all files are processed
                        last(),
                    );
                }),
            )
            .subscribe({
                next: (createdLecture: Lecture) => {
                    this.isUploadingPdfs.set(false);
                    this.alertService.success('artemisApp.lecture.pdfUpload.success');
                    this.router.navigate(['course-management', this.courseId, 'lectures', createdLecture.id, 'edit']);
                },
                error: (error: HttpErrorResponse) => {
                    this.isUploadingPdfs.set(false);
                    onError(this.alertService, error);
                },
            });
    }

    /**
     * Creates attachment units for files in an existing lecture
     */
    private createUnitsForExistingLecture(lectureId: number, files: File[]): void {
        this.isUploadingPdfs.set(true);

        from(files)
            .pipe(concatMap((file) => this.createAttachmentUnit(lectureId, file)))
            .subscribe({
                next: () => {
                    // Each unit created successfully
                },
                error: (error: HttpErrorResponse) => {
                    this.isUploadingPdfs.set(false);
                    onError(this.alertService, error);
                },
                complete: () => {
                    this.isUploadingPdfs.set(false);
                    this.alertService.success('artemisApp.lecture.pdfUpload.success');
                    this.router.navigate(['course-management', this.courseId, 'lectures', lectureId, 'edit']);
                },
            });
    }

    /**
     * Creates a single attachment unit for a file
     */
    private createAttachmentUnit(lectureId: number, file: File) {
        return this.attachmentVideoUnitService.createAttachmentVideoUnitFromFile(lectureId, file);
    }
}
