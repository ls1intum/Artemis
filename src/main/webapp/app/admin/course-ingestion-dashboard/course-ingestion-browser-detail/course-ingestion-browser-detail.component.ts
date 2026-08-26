import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, model, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TumUiMessageComponent, TumUiTagComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { CourseIngestionStoredFieldsComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-stored-fields/course-ingestion-stored-fields.component';
import {
    BrowserSelection,
    CourseBrowserData,
    IndexedContentObject,
    IndexedEntityRecord,
    IngestionTypeCount,
} from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

/** A breadcrumb step back up the tree. */
interface Crumb {
    label: string;
    selection: BrowserSelection;
}

/** One stored content object prepared for display, with the label the design record specifies. */
interface LabelledContentObject {
    label: string;
    object: IndexedContentObject;
}

/**
 * The browser's right pane: what the index holds for whatever is selected in the tree.
 *
 * Everything heavy is fetched per selection rather than with the course. A type's stored records carry the entity body
 * text and a unit's content objects run to hundreds of chunks, so loading either up front would pay for the whole
 * course to look at one part of it.
 */
@Component({
    selector: 'jhi-course-ingestion-browser-detail',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumUiMessageComponent, TumUiTagComponent, CourseIngestionStoredFieldsComponent, TranslateDirective, ArtemisTranslatePipe],
    templateUrl: './course-ingestion-browser-detail.component.html',
})
export class CourseIngestionBrowserDetailComponent {
    private readonly dashboardService = inject(CourseIngestionDashboardService);
    private readonly destroyRef = inject(DestroyRef);

    readonly courseId = input.required<number>();
    readonly data = input.required<CourseBrowserData>();
    readonly typeCounts = input.required<IngestionTypeCount[]>();

    /** Shared with the tree, so a breadcrumb can move the selection back up. */
    readonly selection = model<BrowserSelection | undefined>(undefined);

    readonly records = signal<IndexedEntityRecord[]>([]);
    readonly contentObjects = signal<IndexedContentObject[]>([]);
    readonly loading = signal(false);
    readonly error = signal(false);

    /** The counts behind the type detail's tiles, taken from the coverage row the matrix already has. */
    readonly typeCount = computed<IngestionTypeCount | undefined>(() => {
        const current = this.selection();
        return current?.kind === 'type' ? this.typeCounts().find((count) => count.type === current.type) : undefined;
    });

    /** The entities of the selected type that the index does not hold, so the pane can name them. */
    readonly missingOfType = computed(() => {
        const current = this.selection();
        return current?.kind === 'type' ? this.data().missingEntities.filter((entity) => entity.type === current.type) : [];
    });

    /** The stored record of the selected lecture or unit, once its type's records have been read. */
    readonly selectedRecord = computed<IndexedEntityRecord | undefined>(() => {
        const current = this.selection();
        if (current?.kind === 'lecture') {
            return this.records().find((record) => record.entityId === current.lectureId);
        }
        if (current?.kind === 'unit') {
            return this.records().find((record) => record.entityId === current.unitId);
        }
        return undefined;
    });

    /** Which content collections hold something for the selected unit. */
    readonly unitContentKeys = computed<string[]>(() => {
        const current = this.selection();
        const unitId = current?.kind === 'unit' ? current.unitId : undefined;
        return unitId === undefined
            ? []
            : this.data()
                  .contentPresence.filter((presence) => presence.unitIds.includes(unitId))
                  .map((presence) => presence.key);
    });

    readonly labelledContent = computed<LabelledContentObject[]>(() => this.contentObjects().map((object, index) => ({ label: label(object, index), object })));

    /** The path back up the tree from the current selection. */
    readonly breadcrumbs = computed<Crumb[]>(() => {
        const current = this.selection();
        if (current?.kind === 'unit' || current?.kind === 'collection') {
            const unitId = current.unitId;
            const unit = this.data().entities.find((entity) => entity.type === 'lecture_unit' && entity.entityId === unitId);
            const crumbs: Crumb[] = [];
            if (unit?.lectureId !== undefined) {
                const lecture = this.data().entities.find((entity) => entity.type === 'lecture' && entity.entityId === unit.lectureId);
                crumbs.push({ label: lecture?.title ?? '', selection: { kind: 'lecture', lectureId: unit.lectureId } });
            }
            if (current.kind === 'collection') {
                crumbs.push({ label: unit?.title ?? '', selection: { kind: 'unit', unitId } });
            }
            return crumbs;
        }
        return [];
    });

    constructor() {
        effect(() => {
            const current = this.selection();
            if (!current) {
                return;
            }
            // A lecture or a unit is one row of its type, so the type's records answer both.
            if (current.kind === 'type' || current.kind === 'lecture' || current.kind === 'unit') {
                this.loadRecords(current.kind === 'type' ? current.type : current.kind === 'lecture' ? 'lecture' : 'lecture_unit');
            }
            if (current.kind === 'collection') {
                this.loadContentObjects(current.unitId, current.key);
            }
        });
    }

    /** Moves the selection to a breadcrumb, which the tree picks up and reveals. */
    select(selection: BrowserSelection): void {
        this.selection.set(selection);
    }

    private loadRecords(type: string): void {
        this.loading.set(true);
        this.error.set(false);
        this.contentObjects.set([]);
        this.dashboardService
            .getIndexedEntityRecords(this.courseId(), type)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (records) => {
                    this.records.set(records);
                    this.loading.set(false);
                },
                error: () => {
                    this.records.set([]);
                    this.error.set(true);
                    this.loading.set(false);
                },
            });
    }

    private loadContentObjects(unitId: number, key: string): void {
        this.loading.set(true);
        this.error.set(false);
        this.dashboardService
            .getUnitContent(this.courseId(), unitId, key)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (objects) => {
                    this.contentObjects.set(objects);
                    this.loading.set(false);
                },
                error: () => {
                    this.contentObjects.set([]);
                    this.error.set(true);
                    this.loading.set(false);
                },
            });
    }
}

/**
 * A compact label for a stored content object, per the design record: a page number where the object has one, otherwise
 * a segment start time, otherwise its position in the list.
 */
function label(object: IndexedContentObject, index: number): string {
    const page = object.properties['page_number'] ?? object.properties['display_page_number'];
    if (typeof page === 'number') {
        return `Page ${page}`;
    }
    const segmentStart = object.properties['segment_start_time'];
    if (typeof segmentStart === 'number') {
        return `Segment @ ${segmentStart}s`;
    }
    return `#${index + 1}`;
}
