import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { faDownload, faPlus, faToggleOff } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminScienceService } from 'app/admin/science/admin-science.service';
import { ScienceEnabledCourse, ScienceResearchExportAudit, SelectableCourse } from 'app/admin/science/admin-science.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { downloadFile } from 'app/foundation/util/download.util';
import {
    TumUiButtonDirective,
    TumUiCheckboxComponent,
    TumUiInputDirective,
    TumUiProgressSpinnerComponent,
    TumUiSelectComponent,
    TumUiTableDirective,
    TumUiTagComponent,
} from '@tumaet/ui-angular';

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
        TumUiButtonDirective,
        TumUiCheckboxComponent,
        TumUiInputDirective,
        TumUiProgressSpinnerComponent,
        TumUiSelectComponent,
        TumUiTableDirective,
        TumUiTagComponent,
    ],
})
export class AdminScienceComponent implements OnInit {
    private readonly adminScienceService = inject(AdminScienceService);
    private readonly alertService = inject(AlertService);

    readonly courses = signal<ScienceEnabledCourse[]>([]);
    readonly selectableCourses = signal<SelectableCourse[]>([]);

    /**
     * One readable label per course, so an administrator picks a course rather than recalling its id.
     *
     * The id stays in the label because everything else can repeat or be missing, and picking the wrong course here
     * starts collecting behavioural data on the wrong people.
     */
    readonly selectableCourseOptions = computed(() =>
        this.selectableCourses().map((course) => ({
            id: course.id,
            label: [course.title, course.shortName && `(${course.shortName})`, course.semester, `#${course.id}`].filter(Boolean).join(' '),
        })),
    );
    readonly audits = signal<ScienceResearchExportAudit[]>([]);
    readonly loading = signal(false);
    // Pre-selected rather than empty: the server reads an empty selection as every type, so an untouched form would
    // quietly produce the broadest possible export while showing no types chosen at all.
    readonly selectedEventTypes = signal<ScienceEventType[]>(Object.values(ScienceEventType));

    // Signals rather than plain fields: every one of these is bound in the template, and `courseIdToEnable` is also
    // written back from an HTTP callback, which a zoneless application does not re-render.
    readonly courseIdToEnable = signal<number | undefined>(undefined);
    readonly exportCourseIdsToInclude = signal<number[]>([]);
    readonly exportFrom = signal<string | undefined>(undefined);
    readonly exportTo = signal<string | undefined>(undefined);
    readonly exportPurpose = signal('');

    readonly eventTypes = Object.values(ScienceEventType);
    protected readonly faPlus = faPlus;
    protected readonly faDownload = faDownload;
    protected readonly faToggleOff = faToggleOff;

    ngOnInit(): void {
        this.load();
        // Chosen from a list rather than typed: enabling collection on the wrong course is a data-protection incident,
        // and a bare id field gives an administrator nothing to check a typo against.
        this.adminScienceService.getSelectableCourses().subscribe({
            next: (selectableCourses) => this.selectableCourses.set(selectableCourses),
            error: (error) => this.reportError(error),
        });
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
                this.courseIdToEnable.set(undefined);
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
        // Reported separately from the other two, which are filled in by the time this can happen: the server reads
        // "no types given" as "every type", so unticking everything would otherwise produce the broadest export of all.
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
