import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, model, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TumUiDialogComponent, TumUiMessageComponent, TumUiTagComponent } from '@tumaet/ui-angular';
import { CourseIngestionBrowserTreeComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-browser-tree/course-ingestion-browser-tree.component';
import { CourseIngestionBrowserDetailComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-browser-detail/course-ingestion-browser-detail.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { BrowserSelection, CourseBrowserData, IngestionCoverage } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

/**
 * The per-course content browser: a master-detail modal over what the index actually holds for one course.
 *
 * The coverage matrix stops at a number. This is where an admin goes to see what is behind it, so everything the modal
 * shows is read live for the one course rather than served from the stored projection the matrix reads.
 *
 * The four datasets load together when the modal opens, because the navigation tree is assembled from all of them and
 * cannot be drawn from a subset. The stored objects behind a tree node are deliberately not part of that load; they are
 * fetched when a node is selected.
 */
@Component({
    selector: 'jhi-course-ingestion-browser',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TumUiDialogComponent,
        TumUiMessageComponent,
        TumUiTagComponent,
        CourseIngestionBrowserTreeComponent,
        CourseIngestionBrowserDetailComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
    templateUrl: './course-ingestion-browser.component.html',
})
export class CourseIngestionBrowserComponent {
    private readonly dashboardService = inject(CourseIngestionDashboardService);
    private readonly destroyRef = inject(DestroyRef);

    /** Whether the modal is open. Two-way so the matrix can open it and the dialog can close itself. */
    readonly visible = model(false);

    /** The course row the matrix was showing, which also supplies the per-type counts behind the header chip. */
    readonly course = input.required<IngestionCoverage>();

    readonly data = signal<CourseBrowserData | undefined>(undefined);

    /** What the detail pane is showing. Owned here because both the tree and the detail pane need it. */
    readonly selection = signal<BrowserSelection | undefined>(undefined);
    readonly loading = signal(false);
    readonly error = signal(false);

    /** How many measured types are not fully indexed, shown as the header chip. */
    readonly incompleteTypeCount = computed(() => this.course().typeCounts.filter((count) => count.missing > 0 || count.orphaned > 0).length);

    /** The chip's label key, so one incomplete type does not read as "1 types incomplete" in either language. */
    readonly incompleteTypesLabelKey = computed(() =>
        this.incompleteTypeCount() === 1 ? 'artemisApp.courseIngestionDashboard.browser.typeIncomplete' : 'artemisApp.courseIngestionDashboard.browser.typesIncomplete',
    );

    /** True once loading finished and the index turned out to hold nothing at all for this course. */
    readonly isEmpty = computed(() => {
        const loaded = this.data();
        return loaded !== undefined && loaded.entities.length === 0 && loaded.contentPresence.length === 0;
    });

    /** The course the current data was loaded for, so a re-render of the same course does not refetch it. */
    private loadedCourseId?: number;

    constructor() {
        // Load when the modal opens, and again if it is reopened on a different course. Loading on the course input
        // alone would fetch for every row the matrix renders, which is the cost this modal exists to avoid. The course
        // guard matters because the matrix hands over a new object whenever it reloads its rows, which would otherwise
        // refetch the course already on screen.
        effect(() => {
            const courseId = this.course().courseId;
            if (this.visible() && courseId !== this.loadedCourseId) {
                this.load(courseId);
            }
        });
    }

    /** Closes the modal and drops the loaded data, so reopening always shows current state rather than a stale view. */
    close(): void {
        this.visible.set(false);
        this.loadedCourseId = undefined;
        this.data.set(undefined);
        this.selection.set(undefined);
    }

    private load(courseId: number): void {
        this.loading.set(true);
        this.error.set(false);
        this.dashboardService
            .getCourseBrowserData(courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (data) => {
                    this.loadedCourseId = courseId;
                    this.data.set(data);
                    this.selection.set(undefined);
                    this.loading.set(false);
                },
                error: () => {
                    this.loadedCourseId = undefined;
                    this.data.set(undefined);
                    this.error.set(true);
                    this.loading.set(false);
                },
            });
    }
}
