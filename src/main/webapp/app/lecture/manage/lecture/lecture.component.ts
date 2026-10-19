import dayjs from 'dayjs/esm';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { concatMap, filter, map, toArray } from 'rxjs/operators';
import { LectureService } from '../services/lecture.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { onError } from 'app/foundation/util/global.utils';
import { AlertService } from 'app/foundation/service/alert.service';
import { faChalkboardTeacher, faFileImport, faFilter, faPencilAlt, faPlus, faPuzzlePiece, faSort, faTrash } from '@fortawesome/free-solid-svg-icons';
import { LectureImportComponent } from 'app/lecture/manage/lecture-import/lecture-import.component';
import { Subject, from } from 'rxjs';
import { DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { SortService } from 'app/foundation/service/sort.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { PdfDropZoneComponent } from '../pdf-drop-zone/pdf-drop-zone.component';
import { PdfUploadTarget, PdfUploadTargetDialogComponent } from '../pdf-upload-target-dialog/pdf-upload-target-dialog.component';
import { AttachmentVideoUnitService } from '../lecture-units/services/attachment-video-unit.service';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { PDF_UPLOAD_CONFIRMATION_STATE_KEY, PdfUploadConfirmation } from 'app/lecture/manage/lecture-update/pdf-upload-confirmation.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumAetUiEmptyStateComponent } from '@tumaet/ui-angular';

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
        CourseTitleBarTitleComponent,
        CourseTitleBarTitleDirective,
        CourseTitleBarActionsDirective,
        PdfDropZoneComponent,
        PdfUploadTargetDialogComponent,
        ArtemisTranslatePipe,
        TumAetUiEmptyStateComponent,
    ],
})
export class LectureComponent implements OnInit, OnDestroy {
    private lectureService = inject(LectureService);
    private attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private alertService = inject(AlertService);
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);
    private sortService = inject(SortService);

    readonly lectures = signal<Lecture[]>([]);
    isUploadingPdfs = signal(false);
    /** The PDFs dropped last; the dialog asks where they go. */
    readonly droppedPdfFiles = signal<File[]>([]);
    readonly isPdfUploadTargetDialogVisible = signal(false);
    readonly filteredLectures = signal<Lecture[]>([]);
    readonly loaded = signal(false);
    courseId!: number; // set in ngOnInit() from route params

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
    faChalkboardTeacher = faChalkboardTeacher;
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
        const dialogRef = this.dialogService.open(LectureImportComponent, {
            header: this.translateService.instant('artemisApp.lecture.import.label'),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            draggable: false,
        });

        dialogRef?.onClose.subscribe((result: Lecture | undefined) => {
            if (result) {
                this.lectureService
                    .import(this.courseId, result.id!)
                    .pipe(
                        filter((res: HttpResponse<Lecture>) => res.ok),
                        map((res: HttpResponse<Lecture>) => res.body),
                        filter((body): body is Lecture => body != undefined),
                    )
                    .subscribe({
                        next: (res: Lecture) => {
                            this.lectures.set([...this.lectures(), res]);
                            void this.router.navigate(['course-management', res.course!.id, 'lectures', res.id]);
                        },
                        error: (res: HttpErrorResponse) => onError(this.alertService, res),
                    });
            }
        });
    }

    private deleteLectureFromDisplayedLectures(lectureId: number) {
        this.dialogErrorSource.next('');
        this.lectures.set(this.lectures().filter((lecture) => lecture.id !== lectureId));
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
        this.sortService.sortByProperty(this.filteredLectures(), this.predicate, this.ascending);
    }

    private loadAll() {
        this.lectureService
            .findAllByCourseId(this.courseId)
            .pipe(
                filter((res: HttpResponse<Lecture[]>) => res.ok),
                map((res: HttpResponse<Lecture[]>) => res.body),
                filter((body): body is Lecture[] => body != undefined),
            )
            .subscribe({
                next: (res: Lecture[]) => {
                    this.lectures.set(
                        res.map((lectureData) => {
                            const lecture = new Lecture();
                            hydrate(lecture, lectureData);
                            return lecture;
                        }),
                    );
                    this.applyFilters();
                    this.loaded.set(true);
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
            this.filteredLectures.set(this.lectures());
        } else {
            // Get the current system time
            const now = dayjs();
            // Initialize empty arrays for filtered Lectures
            let filteredLectures: Array<Lecture> = [];

            // update filteredLectures based on the selected filter option checkboxes
            const pastLectures = this.lectures().filter((lecture) => lecture.endDate?.isBefore(now));
            const currentLectures = this.lectures().filter((lecture) => {
                if (lecture.startDate && lecture.endDate) {
                    return lecture.startDate.isSameOrBefore(now) && lecture.endDate.isSameOrAfter(now);
                } else if (lecture.startDate) {
                    return lecture.startDate.isSameOrBefore(now);
                } else if (lecture.endDate) {
                    return lecture.endDate.isSameOrAfter(now);
                }
                return false;
            });
            const futureLectures = this.lectures().filter((lecture) => lecture.startDate?.isAfter(now));
            const unspecifiedDatesLectures = this.lectures().filter((lecture) => lecture.startDate === undefined && lecture.endDate === undefined);

            filteredLectures = this.activeFilters.has(LectureDateFilter.PAST) ? filteredLectures.concat(pastLectures) : filteredLectures;
            filteredLectures = this.activeFilters.has(LectureDateFilter.CURRENT) ? filteredLectures.concat(currentLectures) : filteredLectures;
            filteredLectures = this.activeFilters.has(LectureDateFilter.FUTURE) ? filteredLectures.concat(futureLectures) : filteredLectures;
            filteredLectures = this.activeFilters.has(LectureDateFilter.UNSPECIFIED) ? filteredLectures.concat(unspecifiedDatesLectures) : filteredLectures;
            this.filteredLectures.set(filteredLectures);
        }

        this.sortRows();
    }

    navigateToLectureCreationPage(): void {
        void this.router.navigate(['course-management', this.courseId, 'lectures', 'new'], {
            state: { existingLectures: this.lectures() },
        });
    }

    /**
     * Handles PDF files dropped on the drop zone
     * Opens a dialog to select target lecture (new or existing)
     */
    onPdfFilesDropped(files: File[]): void {
        this.droppedPdfFiles.set(files);
        this.isPdfUploadTargetDialogVisible.set(true);
    }

    onPdfUploadTargetSelected(target: PdfUploadTarget): void {
        const files = this.droppedPdfFiles();
        if (target.targetType === 'new' && target.newLectureTitle) {
            this.createLectureWithUnits(target.newLectureTitle, files);
        } else if (target.targetType === 'existing' && target.lectureId) {
            this.createUnitsForExistingLecture(target.lectureId, files);
        }
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
                    this.lectures.set([...this.lectures(), createdLecture]);
                    this.applyFilters();

                    // Create attachment units sequentially to maintain order
                    return from(files).pipe(
                        concatMap((file) => this.createAttachmentUnit(createdLecture.id!, file)),
                        toArray(),
                        map((responses) => ({ lecture: createdLecture, responses })),
                    );
                }),
            )
            .subscribe({
                next: ({ lecture: createdLecture, responses }) => {
                    this.isUploadingPdfs.set(false);
                    this.openEditorWithConfirmation(createdLecture.id!, true, files, responses);
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
            .pipe(
                concatMap((file) => this.createAttachmentUnit(lectureId, file)),
                toArray(),
            )
            .subscribe({
                next: (responses) => {
                    this.isUploadingPdfs.set(false);
                    this.openEditorWithConfirmation(lectureId, false, files, responses);
                },
                error: (error: HttpErrorResponse) => {
                    this.isUploadingPdfs.set(false);
                    onError(this.alertService, error);
                },
            });
    }

    /**
     * Opens the lecture editor, which confirms the upload there until the user dismisses it: a notification that disappears after a few seconds
     * is too easily missed while the page changes underneath it.
     */
    private openEditorWithConfirmation(lectureId: number, lectureCreated: boolean, files: File[], responses: HttpResponse<AttachmentVideoUnit>[]): void {
        const releaseDates = responses
            .map((response) => response.body?.releaseDate)
            .filter((releaseDate) => !!releaseDate)
            .map((releaseDate) => dayjs(releaseDate));
        const earliestReleaseDate = releaseDates.reduce<dayjs.Dayjs | undefined>((earliest, date) => (!earliest || date.isBefore(earliest) ? date : earliest), undefined);
        const confirmation: PdfUploadConfirmation = {
            lectureCreated,
            fileNames: files.map((file) => file.name),
            releaseDate: earliestReleaseDate?.toISOString(),
        };
        void this.router.navigate(['course-management', this.courseId, 'lectures', lectureId, 'edit'], {
            state: { [PDF_UPLOAD_CONFIRMATION_STATE_KEY]: confirmation },
        });
    }

    /**
     * Creates a single attachment unit for a file
     */
    private createAttachmentUnit(lectureId: number, file: File) {
        return this.attachmentVideoUnitService.createAttachmentVideoUnitFromFile(lectureId, file);
    }
}
