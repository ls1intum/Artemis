import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { faDownload, faPlus, faToggleOff } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminScienceService } from 'app/admin/science/admin-science.service';
import { ScienceEnabledCourse, ScienceResearchExportAudit } from 'app/admin/science/admin-science.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { TumUiButtonDirective } from 'app/shared-ui/tum-ui/button/tum-ui-button.directive';
import { TumUiCheckboxComponent } from 'app/shared-ui/tum-ui/checkbox/tum-ui-checkbox.component';
import { TumUiInputDirective } from 'app/shared-ui/tum-ui/input/tum-ui-input.directive';
import { TumUiProgressSpinnerComponent } from 'app/shared-ui/tum-ui/progress-spinner/tum-ui-progress-spinner.component';
import { TumUiTableDirective } from 'app/shared-ui/tum-ui/table-directive/tum-ui-table.directive';
import { TumUiTagComponent } from 'app/shared-ui/tum-ui/tag/tum-ui-tag.component';

@Component({
    selector: 'jhi-admin-science',
    templateUrl: './admin-science.component.html',
    styleUrl: './admin-science.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        FaIconComponent,
        AdminTitleBarTitleDirective,
        TumUiButtonDirective,
        TumUiCheckboxComponent,
        TumUiInputDirective,
        TumUiProgressSpinnerComponent,
        TumUiTableDirective,
        TumUiTagComponent,
    ],
})
export class AdminScienceComponent implements OnInit {
    private readonly adminScienceService = inject(AdminScienceService);
    private readonly alertService = inject(AlertService);

    readonly courses = signal<ScienceEnabledCourse[]>([]);
    readonly audits = signal<ScienceResearchExportAudit[]>([]);
    readonly loading = signal(false);
    readonly selectedEventTypes = signal<ScienceEventType[]>([]);

    courseIdToEnable?: number;
    exportCourseIds = '';
    exportFrom?: string;
    exportTo?: string;
    exportPurpose = '';

    protected readonly eventTypes = Object.values(ScienceEventType);
    protected readonly faPlus = faPlus;
    protected readonly faDownload = faDownload;
    protected readonly faToggleOff = faToggleOff;

    ngOnInit(): void {
        this.load();
    }

    load(): void {
        this.loading.set(true);
        this.adminScienceService.getCourses().subscribe({
            next: (courses) => {
                this.courses.set(courses);
                this.loading.set(false);
            },
            error: () => {
                this.loading.set(false);
                this.alertService.error('error.unexpectedError');
            },
        });
        this.adminScienceService.getExportAudits().subscribe((audits) => this.audits.set(audits));
    }

    enableCourse(): void {
        if (!this.courseIdToEnable) {
            return;
        }
        this.adminScienceService.enableCourse(this.courseIdToEnable).subscribe({
            next: () => {
                this.courseIdToEnable = undefined;
                this.load();
            },
            error: () => this.alertService.error('error.unexpectedError'),
        });
    }

    disableCourse(course: ScienceEnabledCourse): void {
        this.adminScienceService.disableCourse(course.courseId).subscribe({
            next: () => this.load(),
            error: () => this.alertService.error('error.unexpectedError'),
        });
    }

    toggleEventType(eventType: ScienceEventType, checked: boolean): void {
        const selected = this.selectedEventTypes();
        this.selectedEventTypes.set(checked ? [...selected, eventType] : selected.filter((type) => type !== eventType));
    }

    createExport(): void {
        const courseIds = this.exportCourseIds
            .split(',')
            .map((courseId) => Number(courseId.trim()))
            .filter((courseId) => Number.isFinite(courseId));
        if (courseIds.length === 0 || this.exportPurpose.trim().length === 0) {
            this.alertService.error('At least one course id and a purpose are required.');
            return;
        }
        this.adminScienceService
            .createExport({
                courseIds,
                from: this.exportFrom ? new Date(this.exportFrom).toISOString() : undefined,
                to: this.exportTo ? new Date(this.exportTo).toISOString() : undefined,
                eventTypes: this.selectedEventTypes(),
                purpose: this.exportPurpose,
            })
            .subscribe({
                next: (blob) => {
                    const url = window.URL.createObjectURL(blob);
                    const anchor = document.createElement('a');
                    anchor.href = url;
                    anchor.download = 'science-research-export.csv';
                    anchor.click();
                    window.URL.revokeObjectURL(url);
                    this.load();
                },
                error: () => this.alertService.error('error.unexpectedError'),
            });
    }
}
