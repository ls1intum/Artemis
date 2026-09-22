import { ChangeDetectionStrategy, Component, computed, effect, input, model, signal } from '@angular/core';
import {
    IconDefinition,
    faAlignLeft,
    faBook,
    faBookOpen,
    faChevronDown,
    faChevronRight,
    faCircleQuestion,
    faClosedCaptioning,
    faFileLines,
    faGraduationCap,
    faHashtag,
    faImage,
    faLayerGroup,
    faListCheck,
} from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import {
    BrowserSelection,
    IndexedContentPresence,
    IndexedEntity,
    IngestionTypeCount,
    MissingContent,
    MissingEntity,
    selectionKey,
} from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

/** The metadata types listed in the scoreboard, in a fixed order so the list does not reshuffle between courses. */
const SCOREBOARD_TYPES = ['exercise', 'lecture', 'lecture_unit', 'exam', 'faq', 'channel', 'course'] as const;

/** Icons per metadata type and per content key, from the design record's icon map. */
const TYPE_ICONS: Record<string, IconDefinition> = {
    exercise: faListCheck,
    lecture: faBookOpen,
    lecture_unit: faFileLines,
    exam: faGraduationCap,
    faq: faCircleQuestion,
    channel: faHashtag,
    course: faBook,
};

const CONTENT_ICONS: Record<string, IconDefinition> = {
    slides: faImage,
    transcript: faClosedCaptioning,
    unit_summary: faAlignLeft,
    segments: faLayerGroup,
};

/**
 * A node the template can render without computing anything. The key and the selection are built once with the tree
 * rather than recomputed for every node on every change detection pass, which is what the template used to do.
 */
interface TreeNode {
    key: string;
    selection: BrowserSelection;
}

/** One content collection under a lecture unit. */
interface ContentNode extends TreeNode {
    contentKey: string;
}

/** One lecture unit in the tree, with the content collections that actually hold something for it. */
interface UnitNode extends TreeNode {
    unitId: number;
    title: string;
    content: ContentNode[];
    /** False when content the unit should have was never ingested. */
    complete: boolean;
}

/**
 * Content left in an Iris collection for a lecture unit that no longer exists in the database or the index. It has no
 * title and no completeness: the unit is gone, so the id is all that identifies it and the content is stale by
 * definition.
 */
interface OrphanedContentNode extends TreeNode {
    unitId: number;
    content: ContentNode[];
}

/** One lecture in the tree. {@link indexed} is false when only its units are indexed and the lecture itself is not. */
interface LectureNode extends TreeNode {
    lectureId: number;
    title: string;
    indexed: boolean;
    units: UnitNode[];
    /** False when the lecture is not indexed itself, or any of its units is missing content. */
    complete: boolean;
}

/** One row of the metadata scoreboard. */
interface ScoreboardRow extends TreeNode {
    type: string;
    indexed: number;
    expected: number;
    icon: IconDefinition;
    dotClass: string;
}

/**
 * The browser's left pane: a scoreboard of the measured metadata types above a Lecture to Unit to collection drill tree.
 *
 * The tree is assembled entirely from the payloads the modal already loaded, so opening a node costs nothing and the
 * structure always agrees with what those reads returned.
 *
 * A lecture whose units are indexed but which is not itself indexed still gets a node, marked as not indexed. Dropping
 * those units would hide exactly the kind of gap an admin opens this tool to find.
 */
@Component({
    selector: 'jhi-course-ingestion-browser-tree',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
    templateUrl: './course-ingestion-browser-tree.component.html',
    styleUrl: './course-ingestion-browser-tree.component.scss',
})
export class CourseIngestionBrowserTreeComponent {
    readonly entities = input.required<IndexedEntity[]>();
    readonly contentPresence = input.required<IndexedContentPresence[]>();
    readonly typeCounts = input.required<IngestionTypeCount[]>();
    readonly contentGaps = input.required<MissingContent[]>();
    /** Entities the database expects but the index does not hold, so a not-indexed lecture node can still carry its
     *  real name instead of reading "Untitled lecture". */
    readonly missingEntities = input.required<MissingEntity[]>();

    /** The current selection, shared with the detail pane through the modal. */
    readonly selection = model<BrowserSelection | undefined>(undefined);

    protected readonly faChevronRight = faChevronRight;
    protected readonly faChevronDown = faChevronDown;
    protected readonly contentIcons = CONTENT_ICONS;

    /** Which lecture and unit nodes are open, by selection key. */
    private readonly expandedKeys = signal<ReadonlySet<string>>(new Set());

    /** One row per measured type, in fixed order, whether or not the course has any of that type. */
    protected readonly scoreboard = computed<ScoreboardRow[]>(() => {
        const counts = new Map(this.typeCounts().map((count) => [count.type, count]));
        return SCOREBOARD_TYPES.map((type) => {
            const count = counts.get(type);
            const indexed = count?.indexed ?? 0;
            const expected = count?.expected ?? 0;
            const complete = (count?.missing ?? 0) === 0 && (count?.orphaned ?? 0) === 0;
            const selection: BrowserSelection = { kind: 'type', type };
            return {
                key: selectionKey(selection),
                selection,
                type,
                indexed,
                expected,
                icon: TYPE_ICONS[type],
                dotClass: expected === 0 && indexed === 0 ? 'text-muted-color' : complete ? 'text-state-success' : 'text-state-danger',
            };
        });
    });

    /** The Lecture to Unit to collection tree, assembled from the indexed entities and the content presence sets. */
    protected readonly lectures = computed<LectureNode[]>(() => {
        const entities = this.entities();
        const unitIdsByContentKey = this.contentPresence().map((presence) => ({ key: presence.key, unitIds: new Set(presence.unitIds) }));
        const unitsWithGaps = new Set(this.contentGaps().map((gap) => gap.lectureUnitId));

        const lectureTitles = new Map<number, string>();
        for (const entity of entities) {
            if (entity.type === 'lecture') {
                lectureTitles.set(entity.entityId, entity.title ?? '');
            }
        }
        // A lecture that is not indexed still needs a name for the node its indexed units are nested under. The
        // server already resolved this title from the database; indexed status is read from lectureTitles alone, so
        // filling a title in here from the missing side can never make a not-indexed lecture read as indexed.
        const missingLectureTitles = new Map<number, string>();
        for (const missing of this.missingEntities()) {
            if (missing.type === 'lecture') {
                missingLectureTitles.set(missing.entityId, missing.title ?? '');
            }
        }

        const contentNodesFor = (unitId: number): ContentNode[] =>
            unitIdsByContentKey
                .filter((content) => content.unitIds.has(unitId))
                .map((content) => {
                    const contentSelection: BrowserSelection = { kind: 'collection', unitId, key: content.key };
                    return { key: selectionKey(contentSelection), selection: contentSelection, contentKey: content.key };
                });

        const unitNode = (unitId: number, title: string): UnitNode => {
            const selection: BrowserSelection = { kind: 'unit', unitId };
            return { key: selectionKey(selection), selection, unitId, title, content: contentNodesFor(unitId), complete: !unitsWithGaps.has(unitId) };
        };

        const unitsByLecture = new Map<number, UnitNode[]>();
        const addUnit = (lectureId: number, unit: UnitNode) => unitsByLecture.set(lectureId, [...(unitsByLecture.get(lectureId) ?? []), unit]);

        for (const entity of entities) {
            if (entity.type !== 'lecture_unit' || entity.lectureId === undefined) {
                continue;
            }
            addUnit(entity.lectureId, unitNode(entity.entityId, entity.title ?? ''));
        }

        // A unit the database still has but the index does not belongs under its lecture like any other: only its
        // metadata is absent, which the scoreboard already counts, while its content is real. Leaving it out was what
        // made that content look like the leftovers of a unit nobody has any more. A unit with nothing stored is left
        // out, the same way a lecture with nothing under it gets no node.
        const unitIdsFromTheIndex = new Set(entities.filter((entity) => entity.type === 'lecture_unit').map((entity) => entity.entityId));
        for (const missing of this.missingEntities()) {
            if (missing.type !== 'lecture_unit' || missing.lectureId === undefined || unitIdsFromTheIndex.has(missing.entityId)) {
                continue;
            }
            const unit = unitNode(missing.entityId, missing.title ?? '');
            if (unit.content.length > 0) {
                addUnit(missing.lectureId, unit);
            }
        }

        // The union of indexed lectures and lectures referenced by an indexed unit, so a unit is never dropped just
        // because its lecture is missing from the index.
        const lectureIds = new Set<number>([...lectureTitles.keys(), ...unitsByLecture.keys()]);
        return [...lectureIds]
            .map((lectureId) => {
                const selection: BrowserSelection = { kind: 'lecture', lectureId };
                const units = (unitsByLecture.get(lectureId) ?? []).sort((a, b) => a.title.localeCompare(b.title));
                return {
                    key: selectionKey(selection),
                    selection,
                    lectureId,
                    title: lectureTitles.get(lectureId) ?? missingLectureTitles.get(lectureId) ?? '',
                    indexed: lectureTitles.has(lectureId),
                    units,
                    // A lecture is only as complete as what sits under it, so an unindexed lecture or any unit missing
                    // content marks the whole branch, which is what makes a collapsed tree worth scanning.
                    complete: lectureTitles.has(lectureId) && units.every((unit) => unit.complete),
                };
            })
            .sort((a, b) => a.title.localeCompare(b.title));
    });

    /**
     * Content whose lecture unit the database no longer has, grouped on its own because there is no lecture left to
     * nest it under.
     *
     * A unit is only counted here when the index does not hold it and the database does not expect it either. Absence
     * from the index alone says nothing: a unit whose metadata ingestion failed is still in the database, and its
     * content is valid rather than stale, so it is drawn under its lecture like any other unit instead.
     *
     * The unit itself is not selectable: nothing is stored about it any more, so its collections are all there is to
     * open, and the coverage row counts those objects as orphans.
     */
    protected readonly orphanedContentUnits = computed<OrphanedContentNode[]>(() => {
        const presence = this.contentPresence();
        const knownUnitIds = new Set([
            ...this.entities()
                .filter((entity) => entity.type === 'lecture_unit')
                .map((entity) => entity.entityId),
            ...this.missingEntities()
                .filter((missing) => missing.type === 'lecture_unit')
                .map((missing) => missing.entityId),
        ]);
        const orphanedUnitIds = [...new Set(presence.flatMap((entry) => entry.unitIds))].filter((unitId) => !knownUnitIds.has(unitId)).sort((a, b) => a - b);

        return orphanedUnitIds.map((unitId) => {
            const selection: BrowserSelection = { kind: 'unit', unitId };
            return {
                key: selectionKey(selection),
                selection,
                unitId,
                content: presence
                    .filter((entry) => entry.unitIds.includes(unitId))
                    .map((entry) => {
                        const contentSelection: BrowserSelection = { kind: 'collection', unitId, key: entry.key };
                        return { key: selectionKey(contentSelection), selection: contentSelection, contentKey: entry.key };
                    }),
            };
        });
    });

    /** Which lecture each unit belongs to, so the ancestors of any selection can be derived from the tree itself. */
    private readonly lectureIdByUnitId = computed(() => {
        const byUnit = new Map<number, number>();
        for (const lecture of this.lectures()) {
            for (const unit of lecture.units) {
                byUnit.set(unit.unitId, lecture.lectureId);
            }
        }
        return byUnit;
    });

    constructor() {
        // Reveal whatever is selected, whoever selected it. Doing this here rather than in the click handler is what
        // makes it hold for a selection set from outside the tree, which is how a breadcrumb or a contextual jump
        // arrives. Selecting a lecture or a unit also opens that node, since opening is what choosing one is for.
        effect(() => {
            const selection = this.selection();
            if (!selection) {
                return;
            }
            const keysToOpen = this.ancestorKeys(selection);
            if (selection.kind === 'lecture' || selection.kind === 'unit') {
                keysToOpen.push(selectionKey(selection));
            }
            if (keysToOpen.length === 0) {
                return;
            }
            const expanded = new Set(this.expandedKeys());
            const sizeBefore = expanded.size;
            keysToOpen.forEach((key) => expanded.add(key));
            if (expanded.size !== sizeBefore) {
                this.expandedKeys.set(expanded);
            }
        });
    }

    /** The keys of the nodes a selection sits inside, outermost first. */
    private ancestorKeys(selection: BrowserSelection): string[] {
        const unitId = selection.kind === 'unit' ? selection.unitId : selection.kind === 'collection' ? selection.unitId : undefined;
        if (unitId === undefined) {
            return [];
        }
        const lectureId = this.lectureIdByUnitId().get(unitId);
        const keys = lectureId === undefined ? [] : [selectionKey({ kind: 'lecture', lectureId })];
        if (selection.kind === 'collection') {
            keys.push(selectionKey({ kind: 'unit', unitId }));
        }
        return keys;
    }

    protected isExpanded(key: string): boolean {
        return this.expandedKeys().has(key);
    }

    /** The selected node's key, computed once per selection rather than per rendered node. */
    protected readonly selectedKey = computed(() => {
        const current = this.selection();
        return current === undefined ? undefined : selectionKey(current);
    });

    protected toggle(key: string): void {
        const expanded = new Set(this.expandedKeys());
        if (!expanded.delete(key)) {
            expanded.add(key);
        }
        this.expandedKeys.set(expanded);
    }

    /** Selects a node. Revealing it is handled centrally, so this behaves the same however the selection is made. */
    protected select(selection: BrowserSelection): void {
        this.selection.set(selection);
    }
}
