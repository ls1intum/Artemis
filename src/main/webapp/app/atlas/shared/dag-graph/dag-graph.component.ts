import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, TemplateRef, computed, contentChild, inject, input, signal } from '@angular/core';
import { Graph, layout as dagreLayout } from '@dagrejs/dagre';
import { curveBundle, line } from 'd3-shape';
import { DagGraphEdge, DagGraphLayout, DagGraphLayoutEdge, DagGraphLayoutNode, DagGraphNode, GraphNodeDimension, GraphPosition } from 'app/atlas/shared/dag-graph/dag-graph.model';

interface PanState {
    pointerX: number;
    pointerY: number;
    panX: number;
    panY: number;
}

/**
 * Renders a directed graph laid out left-to-right with dagre, as a pannable/zoomable SVG.
 *
 * It replaces the small slice of `@swimlane/ngx-graph` that Artemis used for competency graphs.
 * The layout is delegated to the framework-agnostic `@dagrejs/dagre`; everything else (rendering,
 * pan, zoom, minimap) is plain Angular SVG with no zone.js or RxJS coupling.
 *
 * Node, edge, and defs content are projected via ng-templates referenced as
 * `#nodeTemplate`, `#linkTemplate`, and `#defsTemplate`. Templates receive the corresponding
 * {@link DagGraphLayoutNode} / {@link DagGraphLayoutEdge} as their implicit context, so click
 * handlers and styling stay in the consumer.
 *
 * Nodes without a measured dimension are laid out with default sizes and the graph stays invisible
 * (`visibility: hidden`, so contents remain measurable) until every node has reported its size —
 * consumers update the node objects' `dimension` and pass a new array to trigger the final layout.
 *
 * Although named "DAG", the component does not require acyclic input: dagre lays cycles out by
 * temporarily reversing back-edges, so cyclic or self-referential data is tolerated rather than rejected.
 *
 * Accessibility: interaction is pointer/wheel only; there is no keyboard navigation. The root SVG
 * exposes `role="group"` and an optional {@link ariaLabel}; consumers that need accessible selection
 * should provide an alternative (e.g. a list) alongside the graph.
 */
@Component({
    selector: 'jhi-dag-graph',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgTemplateOutlet],
    templateUrl: './dag-graph.component.html',
    styleUrl: './dag-graph.component.scss',
})
export class DagGraphComponent implements OnDestroy {
    private static readonly DEFAULT_NODE_WIDTH = 100;
    private static readonly DEFAULT_NODE_HEIGHT = 45.59;
    private static readonly MIN_ZOOM = 0.1;
    private static readonly MAX_ZOOM = 4;
    private static readonly ZOOM_WHEEL_SENSITIVITY = 1.01;
    private static readonly MINIMAP_MARGIN = 10;

    /** Graph nodes. A node becomes part of the layout once its `dimension` is set (see class docs). */
    readonly nodes = input.required<DagGraphNode[]>();
    /** Graph edges. Edges whose `source`/`target` do not match a node id are skipped. */
    readonly edges = input.required<DagGraphEdge[]>();
    /** Whether dragging the background pans the graph. */
    readonly enablePan = input(true);
    /** Whether the mouse wheel zooms the graph; when false the wheel keeps scrolling the page. */
    readonly enableZoom = input(true);
    /** Whether to render the static minimap (with a viewport indicator) in the top-right corner. */
    readonly showMiniMap = input(true);
    /** Maximum minimap width in px; the minimap is scaled down to fit within this. Must be positive. */
    readonly miniMapMaxWidth = input(200);
    /** Maximum minimap height in px; the minimap is scaled down to fit within this. Must be positive. */
    readonly miniMapMaxHeight = input(150);
    /** Optional accessible label for the root SVG (`aria-label`). Pass a translated string. */
    readonly ariaLabel = input<string>();

    /** Optional `<defs>` content (e.g. arrow markers), projected via `#defsTemplate`. */
    readonly defsTemplate = contentChild<TemplateRef<unknown>>('defsTemplate');
    /** Required node renderer, projected via `#nodeTemplate`; receives a {@link DagGraphLayoutNode} as `$implicit`. */
    readonly nodeTemplate = contentChild.required<TemplateRef<{ $implicit: DagGraphLayoutNode }>>('nodeTemplate');
    /** Optional edge renderer, projected via `#linkTemplate`; receives a {@link DagGraphLayoutEdge} as `$implicit`. Falls back to a plain arrowed path. */
    readonly edgeTemplate = contentChild<TemplateRef<{ $implicit: DagGraphLayoutEdge }>>('linkTemplate');

    readonly layout = computed<DagGraphLayout>(() => this.computeLayout(this.nodes(), this.edges()));
    /** True once every node has reported a measured dimension; the graph is hidden until then. */
    readonly allMeasured = computed(() => this.nodes().every((node) => node.dimension !== undefined));

    private readonly panX = signal(0);
    private readonly panY = signal(0);
    private readonly zoomLevel = signal(1);
    protected readonly contentTransform = computed(() => `translate(${this.panX()}, ${this.panY()}) scale(${this.zoomLevel()})`);

    private readonly hostSize = signal<GraphNodeDimension>({ width: 0, height: 0 });
    private panState?: PanState;

    protected readonly minimapScale = computed(() => {
        const { width, height } = this.layout();
        // guard the divisors: a 0 max-dimension would make the scale Infinity and collapse the minimap
        return Math.max(width / Math.max(this.miniMapMaxWidth(), 1), height / Math.max(this.miniMapMaxHeight(), 1), 1);
    });
    protected readonly minimapTransform = computed(() => {
        const scale = this.minimapScale();
        const x = Math.max(this.hostSize().width - this.layout().width / scale - DagGraphComponent.MINIMAP_MARGIN, 0);
        return `translate(${x}, ${DagGraphComponent.MINIMAP_MARGIN}) scale(${1 / scale})`;
    });
    /**
     * Currently visible region of the graph in graph coordinates, for the minimap viewport indicator.
     * Clamped to the graph extent so the indicator never spills outside the minimap box at extreme pan/zoom.
     */
    protected readonly minimapViewport = computed(() => {
        const zoom = this.zoomLevel();
        const { width: graphWidth, height: graphHeight } = this.layout();
        const left = -this.panX() / zoom;
        const top = -this.panY() / zoom;
        const right = left + this.hostSize().width / zoom;
        const bottom = top + this.hostSize().height / zoom;
        const x = Math.max(left, 0);
        const y = Math.max(top, 0);
        return {
            x,
            y,
            width: Math.max(Math.min(right, graphWidth) - x, 0),
            height: Math.max(Math.min(bottom, graphHeight) - y, 0),
        };
    });

    private readonly element = inject(ElementRef);
    private resizeObserver?: ResizeObserver;

    constructor() {
        if (typeof ResizeObserver !== 'undefined') {
            this.resizeObserver = new ResizeObserver((entries) => {
                const contentRect = entries[0]?.contentRect;
                if (contentRect) {
                    this.hostSize.set({ width: contentRect.width, height: contentRect.height });
                }
            });
            this.resizeObserver.observe(this.element.nativeElement);
        }
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
    }

    /**
     * Zooms towards/away from the cursor on wheel, keeping the graph point under the cursor fixed.
     * No-op (and the page keeps scrolling) when zooming is disabled. The zoom is clamped to [MIN_ZOOM, MAX_ZOOM].
     */
    protected onWheel(event: WheelEvent): void {
        if (!this.enableZoom()) {
            return;
        }
        event.preventDefault();
        const factor = Math.pow(DagGraphComponent.ZOOM_WHEEL_SENSITIVITY, -event.deltaY);
        const newZoom = Math.min(Math.max(this.zoomLevel() * factor, DagGraphComponent.MIN_ZOOM), DagGraphComponent.MAX_ZOOM);
        const appliedFactor = newZoom / this.zoomLevel();
        // anchor the zoom at the cursor: keep the graph point under the cursor fixed
        const hostRect = this.element.nativeElement.getBoundingClientRect();
        const cursorX = event.clientX - hostRect.left;
        const cursorY = event.clientY - hostRect.top;
        this.panX.set(cursorX - (cursorX - this.panX()) * appliedFactor);
        this.panY.set(cursorY - (cursorY - this.panY()) * appliedFactor);
        this.zoomLevel.set(newZoom);
    }

    /**
     * Starts a pan gesture. Restricted to the background `.panning-rect` so clicks on projected
     * nodes and edges keep working; uses pointer capture so the drag continues outside the element.
     */
    protected onPointerDown(event: PointerEvent): void {
        // pan only via the background rect so node and edge clicks keep working
        if (!this.enablePan() || event.button !== 0 || !(event.target instanceof Element) || !event.target.classList.contains('panning-rect')) {
            return;
        }
        this.panState = { pointerX: event.clientX, pointerY: event.clientY, panX: this.panX(), panY: this.panY() };
        event.target.setPointerCapture?.(event.pointerId);
    }

    /** Translates the graph by the pointer delta while a pan gesture is active. */
    protected onPointerMove(event: PointerEvent): void {
        if (!this.panState) {
            return;
        }
        this.panX.set(this.panState.panX + event.clientX - this.panState.pointerX);
        this.panY.set(this.panState.panY + event.clientY - this.panState.pointerY);
    }

    /** Ends the current pan gesture (pointer up or cancel). */
    protected onPointerUp(): void {
        this.panState = undefined;
    }

    /**
     * Lays the graph out left-to-right with dagre and derives the SVG geometry: node transforms,
     * edge paths (a `curveBundle` through the dagre points) and, for right-to-left edges, a reversed
     * path so labels read left-to-right. Edges with an unknown source/target are dropped before layout
     * (graphlib would otherwise create ghost nodes); self-loops are kept and laid out by dagre.
     */
    private computeLayout(nodes: DagGraphNode[], edges: DagGraphEdge[]): DagGraphLayout {
        const graph = new Graph({ multigraph: true });
        // same settings ngx-graph's dagre layouts used, for visual continuity
        graph.setGraph({ rankdir: 'LR', marginx: 20, marginy: 20, nodesep: 50, ranksep: 100, edgesep: 100 });
        const nodeIds = new Set(nodes.map((node) => node.id));
        for (const node of nodes) {
            graph.setNode(node.id, {
                width: node.dimension?.width || DagGraphComponent.DEFAULT_NODE_WIDTH,
                height: node.dimension?.height || DagGraphComponent.DEFAULT_NODE_HEIGHT,
            });
        }
        // skip edges with missing endpoints — graphlib would silently create ghost nodes for them
        const validEdges = edges.filter((edge) => nodeIds.has(edge.source) && nodeIds.has(edge.target));
        for (const edge of validEdges) {
            graph.setEdge(edge.source, edge.target, {}, edge.id);
        }
        try {
            dagreLayout(graph);
        } catch (error) {
            // dagre can throw (e.g. stack overflow on pathologically deep chains); degrade to an empty
            // graph instead of breaking change detection. Realistic competency graphs never hit this.
            globalThis.console.error('DagGraphComponent: dagre layout failed, rendering an empty graph', error);
            return { nodes: [], edges: [], width: 0, height: 0 };
        }

        const layoutNodes = nodes.map((node): DagGraphLayoutNode => {
            const { x, y, width, height } = graph.node(node.id);
            return {
                node,
                dimension: { width, height },
                position: { x, y },
                transform: `translate(${x - width / 2}, ${y - height / 2})`,
            };
        });

        const lineGenerator = line<GraphPosition>()
            .x((point) => point.x)
            .y((point) => point.y)
            .curve(curveBundle.beta(1));
        const layoutEdges = validEdges.map((edge): DagGraphLayoutEdge => {
            const points: GraphPosition[] = graph.edge(edge.source, edge.target, edge.id)?.points ?? [];
            const path = lineGenerator(points) ?? '';
            // for edges running right-to-left, attach labels to a reversed path so they read left-to-right
            const reversed = points.length > 1 && points[0].x > points[points.length - 1].x;
            return {
                edge,
                points,
                path,
                textPath: reversed ? (lineGenerator([...points].reverse()) ?? '') : path,
                dominantBaseline: reversed ? 'text-before-edge' : 'text-after-edge',
            };
        });

        const graphLabel = graph.graph();
        return {
            nodes: layoutNodes,
            edges: layoutEdges,
            width: Number.isFinite(graphLabel.width) ? graphLabel.width! : 0,
            height: Number.isFinite(graphLabel.height) ? graphLabel.height! : 0,
        };
    }
}
