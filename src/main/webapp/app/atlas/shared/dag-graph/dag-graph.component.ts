import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, TemplateRef, computed, contentChild, inject, input, signal } from '@angular/core';
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
 * Renders a directed acyclic graph laid out with dagre (left-to-right).
 *
 * Node, edge, and defs content are projected via ng-templates referenced as
 * #nodeTemplate, #linkTemplate, and #defsTemplate. Templates receive the
 * corresponding {@link DagGraphLayoutNode} / {@link DagGraphLayoutEdge} as
 * their implicit context, so click handlers and styling stay in the consumer.
 *
 * Nodes without a measured dimension are laid out with default sizes and the
 * graph stays invisible (visibility: hidden, so contents remain measurable)
 * until every node reported its size — consumers update the node objects'
 * `dimension` and pass a new array to trigger the final layout.
 */
@Component({
    selector: 'jhi-dag-graph',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgTemplateOutlet],
    templateUrl: './dag-graph.component.html',
    styleUrl: './dag-graph.component.scss',
})
export class DagGraphComponent {
    private static readonly DEFAULT_NODE_WIDTH = 100;
    private static readonly DEFAULT_NODE_HEIGHT = 45.59;
    private static readonly MIN_ZOOM = 0.1;
    private static readonly MAX_ZOOM = 4;
    private static readonly ZOOM_WHEEL_SENSITIVITY = 1.01;
    private static readonly MINIMAP_MARGIN = 10;

    readonly nodes = input.required<DagGraphNode[]>();
    readonly edges = input.required<DagGraphEdge[]>();
    readonly enablePan = input(true);
    readonly enableZoom = input(true);
    readonly showMiniMap = input(true);
    readonly miniMapMaxWidth = input(200);
    readonly miniMapMaxHeight = input(150);

    readonly defsTemplate = contentChild<TemplateRef<unknown>>('defsTemplate');
    readonly nodeTemplate = contentChild.required<TemplateRef<{ $implicit: DagGraphLayoutNode }>>('nodeTemplate');
    readonly edgeTemplate = contentChild<TemplateRef<{ $implicit: DagGraphLayoutEdge }>>('linkTemplate');

    readonly layout = computed<DagGraphLayout>(() => this.computeLayout(this.nodes(), this.edges()));
    readonly allMeasured = computed(() => this.nodes().every((node) => (node.dimension?.width ?? 0) > 0));

    private readonly panX = signal(0);
    private readonly panY = signal(0);
    private readonly zoomLevel = signal(1);
    protected readonly contentTransform = computed(() => `translate(${this.panX()}, ${this.panY()}) scale(${this.zoomLevel()})`);

    private readonly hostSize = signal<GraphNodeDimension>({ width: 0, height: 0 });
    private panState?: PanState;

    protected readonly minimapScale = computed(() => {
        const { width, height } = this.layout();
        return Math.max(width / this.miniMapMaxWidth(), height / this.miniMapMaxHeight(), 1);
    });
    protected readonly minimapTransform = computed(() => {
        const scale = this.minimapScale();
        const x = Math.max(this.hostSize().width - this.layout().width / scale - DagGraphComponent.MINIMAP_MARGIN, 0);
        return `translate(${x}, ${DagGraphComponent.MINIMAP_MARGIN}) scale(${1 / scale})`;
    });
    /** Currently visible region of the graph in graph coordinates, for the minimap viewport indicator. */
    protected readonly minimapViewport = computed(() => {
        const zoom = this.zoomLevel();
        return {
            x: -this.panX() / zoom,
            y: -this.panY() / zoom,
            width: this.hostSize().width / zoom,
            height: this.hostSize().height / zoom,
        };
    });

    private readonly element = inject(ElementRef);

    constructor() {
        if (typeof ResizeObserver !== 'undefined') {
            const resizeObserver = new ResizeObserver((entries) => {
                const contentRect = entries[0]?.contentRect;
                if (contentRect) {
                    this.hostSize.set({ width: contentRect.width, height: contentRect.height });
                }
            });
            resizeObserver.observe(this.element.nativeElement);
            inject(DestroyRef).onDestroy(() => resizeObserver.disconnect());
        }
    }

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

    protected onPointerDown(event: PointerEvent): void {
        // pan only via the background rect so node and edge clicks keep working
        if (!this.enablePan() || event.button !== 0 || !(event.target instanceof Element) || !event.target.classList.contains('panning-rect')) {
            return;
        }
        this.panState = { pointerX: event.clientX, pointerY: event.clientY, panX: this.panX(), panY: this.panY() };
        event.target.setPointerCapture?.(event.pointerId);
    }

    protected onPointerMove(event: PointerEvent): void {
        if (!this.panState) {
            return;
        }
        this.panX.set(this.panState.panX + event.clientX - this.panState.pointerX);
        this.panY.set(this.panState.panY + event.clientY - this.panState.pointerY);
    }

    protected onPointerUp(): void {
        this.panState = undefined;
    }

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
        dagreLayout(graph);

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
