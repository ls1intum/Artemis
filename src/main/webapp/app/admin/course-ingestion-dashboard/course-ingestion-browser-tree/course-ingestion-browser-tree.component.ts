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
import { BrowserSelection, IndexedContentPresence, IndexedEntity, IngestionTypeCount, selectionKey } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

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
}

/** One lecture in the tree. {@link indexed} is false when only its units are indexed and the lecture itself is not. */
interface LectureNode extends TreeNode {
    lectureId: number;
    title: string;
    indexed: boolean;
    units: UnitNode[];
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

        const lectureTitles = new Map<number, string>();
        for (const entity of entities) {
            if (entity.type === 'lecture') {
                lectureTitles.set(entity.entityId, entity.title ?? '');
            }
        }

        const unitsByLecture = new Map<number, UnitNode[]>();
        for (const entity of entities) {
            if (entity.type !== 'lecture_unit') {
                continue;
            }
            const lectureId = entity.lectureId;
            if (lectureId === undefined) {
                continue;
            }
            const unitSelection: BrowserSelection = { kind: 'unit', unitId: entity.entityId };
            const unit: UnitNode = {
                key: selectionKey(unitSelection),
                selection: unitSelection,
                unitId: entity.entityId,
                title: entity.title ?? '',
                content: unitIdsByContentKey
                    .filter((content) => content.unitIds.has(entity.entityId))
                    .map((content) => {
                        const contentSelection: BrowserSelection = { kind: 'collection', unitId: entity.entityId, key: content.key };
                        return { key: selectionKey(contentSelection), selection: contentSelection, contentKey: content.key };
                    }),
            };
            unitsByLecture.set(lectureId, [...(unitsByLecture.get(lectureId) ?? []), unit]);
        }

        // The union of indexed lectures and lectures referenced by an indexed unit, so a unit is never dropped just
        // because its lecture is missing from the index.
        const lectureIds = new Set<number>([...lectureTitles.keys(), ...unitsByLecture.keys()]);
        return [...lectureIds]
            .map((lectureId) => {
                const selection: BrowserSelection = { kind: 'lecture', lectureId };
                return {
                    key: selectionKey(selection),
                    selection,
                    lectureId,
                    title: lectureTitles.get(lectureId) ?? '',
                    indexed: lectureTitles.has(lectureId),
                    units: (unitsByLecture.get(lectureId) ?? []).sort((a, b) => a.title.localeCompare(b.title)),
                };
            })
            .sort((a, b) => a.title.localeCompare(b.title));
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
