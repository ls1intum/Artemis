import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { faDownload, faPlus, faToggleOff } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminScienceService } from 'app/admin/science/admin-science.service';
import { ScienceEnabledCourse, ScienceResearchExportAudit } from 'app/admin/science/admin-science.model';
import { CourseForRoleAssignment } from 'app/account/user/shared/user-course-role.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { downloadFile } from 'app/foundation/util/download.util';
import {
    TumAetUiAutoCompleteComponent,
    TumAetUiAutoCompleteSearchEvent,
    TumAetUiButtonDirective,
    TumAetUiCheckboxComponent,
    TumAetUiFormFieldComponent,
    TumAetUiInputDirective,
    TumAetUiProgressSpinnerComponent,
    TumAetUiTableDirective,
    TumAetUiTagComponent,
} from '@tumaet/ui-angular';

/** Number of courses suggested while an administrator types. */
const COURSE_SUGGESTION_COUNT = 10;

/** A course in the suggestions, with the label the administrator checks before enabling collection for it. */
interface CourseOption {
    id: number;
    label: string;
}

/**
 * Short name first, because it is unique and what administrators know a course by, so a long title truncated in the
 * suggestions cannot hide it: "ALGO25 · Algorithms (WS25) #42". Picking the wrong course here starts collecting
 * behavioural data on the wrong people, so the id is in the label too.
 */
const toCourseOption = (course: CourseForRoleAssignment): CourseOption => {
    const name = [course.shortName, course.title].filter(Boolean).join(' · ');
    return { id: course.id, label: [name, course.semester && `(${course.semester})`, `#${course.id}`].filter(Boolean).join(' ') };
};

@Component({
    selector: 'jhi-admin-science',
    templateUrl: './admin-science.component.html',
    styleUrl: './admin-science.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        AdminTitleBarTitleDirective,
        TumAetUiAutoCompleteComponent,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiFormFieldComponent,
        TumAetUiInputDirective,
        TumAetUiProgressSpinnerComponent,
        TumAetUiTableDirective,
        TumAetUiTagComponent,
    ],
})
export class AdminScienceComponent implements OnInit {
    private readonly adminScienceService = inject(AdminScienceService);
    private readonly alertService = inject(AlertService);

    readonly courses = signal<ScienceEnabledCourse[]>([]);

    // Chosen from search results rather than typed: enabling collection on the wrong course is a data-protection
    // incident, and a bare id field gives an administrator nothing to check a typo against. Searched rather than
    // listed, because offering every course at once does not scale to the number of courses an instance holds.
    readonly courseSuggestions = signal<CourseOption[]>([]);
    /** Either a chosen course or the text typed into the course field. */
    readonly courseInput = signal<CourseOption | string | undefined>(undefined);
    /** The chosen course. Typing into the field again discards it, because the text no longer names a course. */
    readonly courseIdToEnable = computed(() => {
        const value = this.courseInput();
        return value && typeof value === 'object' ? value.id : undefined;
    });
    /** Search texts in the order they were typed. Only the latest one is answered, an earlier search still in flight is cancelled. */
    private readonly searchTexts = new Subject<string>();

    readonly audits = signal<ScienceResearchExportAudit[]>([]);
    readonly loading = signal(false);
    // Pre-selected rather than empty, so that an untouched form exports what it shows. The request always names the
    // types: the server reads an omitted filter as every type and rejects an empty one.
    readonly selectedEventTypes = signal<ScienceEventType[]>(Object.values(ScienceEventType));

    // Signals rather than plain fields: every one of these is bound in the template, and `courseInput` is also written
    // back from an HTTP callback, which a zoneless application does not re-render.
    readonly exportCourseIdsToInclude = signal<number[]>([]);
    readonly exportFrom = signal<string | undefined>(undefined);
    readonly exportTo = signal<string | undefined>(undefined);
    readonly exportPurpose = signal('');

    readonly eventTypes = Object.values(ScienceEventType);
    protected readonly faPlus = faPlus;
    protected readonly faDownload = faDownload;
    protected readonly faToggleOff = faToggleOff;

    constructor() {
        this.searchTexts
            .pipe(
                switchMap((searchText) => this.adminScienceService.searchSelectableCourses(searchText, COURSE_SUGGESTION_COUNT).pipe(catchError(() => of([])))),
                takeUntilDestroyed(),
            )
            .subscribe((courses) => this.courseSuggestions.set(courses.map(toCourseOption)));
    }

    ngOnInit(): void {
        this.load();
    }

    searchCourses(event: TumAetUiAutoCompleteSearchEvent): void {
        this.searchTexts.next(event.query);
    }

    onCourseChange(value: unknown): void {
        this.courseInput.set(value as CourseOption | string | undefined);
    }

    load(): void {
        this.loading.set(true);
        this.adminScienceService.getCourses().subscribe({
            next: (courses) => {
                this.courses.set(courses);
                this.loading.set(false);
            },
            error: (error) => {
                this.loading.set(false);
                this.reportError(error);
            },
        });
        this.adminScienceService.getExportAudits().subscribe({
            next: (audits) => this.audits.set(audits),
            error: (error) => this.reportError(error),
        });
    }

    enableCourse(): void {
        const courseId = this.courseIdToEnable();
        if (!courseId) {
            return;
        }
        this.adminScienceService.enableCourse(courseId).subscribe({
            next: () => {
                this.courseInput.set(undefined);
                this.load();
            },
            error: (error) => this.reportError(error),
        });
    }

    disableCourse(course: ScienceEnabledCourse): void {
        this.adminScienceService.disableCourse(course.courseId).subscribe({
            next: () => this.load(),
            error: (error) => this.reportError(error),
        });
    }

    toggleEventType(eventType: ScienceEventType, checked: boolean): void {
        const remaining = this.selectedEventTypes().filter((type) => type !== eventType);
        // Rebuilt from the remainder rather than appended to, so a repeated "checked" cannot list the same type twice
        // and widen the audited filter beyond what was actually chosen.
        this.selectedEventTypes.set(checked ? [...remaining, eventType] : remaining);
    }

    toggleExportCourse(courseId: number, checked: boolean): void {
        const remaining = this.exportCourseIdsToInclude().filter((id) => id !== courseId);
        this.exportCourseIdsToInclude.set(checked ? [...remaining, courseId] : remaining);
    }

    createExport(): void {
        const courseIds = this.exportCourseIdsToInclude();
        if (courseIds.length === 0 || this.exportPurpose().trim().length === 0) {
            this.alertService.error('artemisApp.admin.science.export.validation');
            return;
        }
        // Reported separately from the other two, which are filled in by the time this can happen. The server would
        // reject an empty selection too (scienceExportMissingEventTypes); checked here so no request is sent for it.
        if (this.selectedEventTypes().length === 0) {
            this.alertService.error('artemisApp.admin.science.export.validationEventTypes');
            return;
        }
        const from = this.exportFrom();
        const to = this.exportTo();
        this.adminScienceService
            .createExport({
                courseIds,
                from: from ? new Date(from).toISOString() : undefined,
                to: to ? new Date(to).toISOString() : undefined,
                eventTypes: this.selectedEventTypes(),
                purpose: this.exportPurpose().trim(),
            })
            .subscribe({
                next: (blob) => {
                    downloadFile(blob, 'science-research-export.csv');
                    // Reloads the audit history so the export that was just recorded appears without a manual refresh.
                    this.load();
                },
                error: (error) => this.reportError(error),
            });
    }

    /**
     * Reports a failed request, preferring what the server said it refused.
     *
     * The export is requested as a Blob, so its error body arrives unreadable and {@code error.message} degrades to a
     * generic transport failure - every rejected export would otherwise look the same. The error header carries the
     * reason regardless of the response type, already prefixed with {@code error.} by {@code HeaderUtil}.
     */
    private reportError(error: HttpErrorResponse): void {
        const serverError = error.headers?.get('X-artemisApp-error');
        if (serverError) {
            this.alertService.error(serverError);
            return;
        }
        this.alertService.error('error.unexpectedError', { error: error.message });
    }
}
