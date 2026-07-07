/**
 * Graph model for the in-house {@link DagGraphComponent}, replacing the types previously imported
 * from `@swimlane/ngx-graph`.
 *
 * There are two tiers:
 * - **Input types** ({@link DagGraphNode}, {@link DagGraphEdge}) are what a consumer builds and binds
 *   to the component's `nodes` / `edges` inputs.
 * - **Layout types** ({@link DagGraphLayoutNode}, {@link DagGraphLayoutEdge}, {@link DagGraphLayout}) are
 *   what the component derives from the dagre layout and exposes to the projected node/edge templates.
 *   They wrap the original input object plus the resolved SVG geometry, so consumers keep their own
 *   typed data (via the `<T>` parameter) while reading positions/paths.
 */

/** Width and height of a node in px (SVG user units). */
export interface GraphNodeDimension {
    width: number;
    height: number;
}

/** A point in graph (pre-pan/zoom) coordinates. */
export interface GraphPosition {
    x: number;
    y: number;
}

/** A node as provided by the consumer. */
export interface DagGraphNode {
    /** Stable, unique id used to track the node and to match edge `source`/`target`. */
    id: string;
    /** Optional human-readable label (rendering is up to the projected node template). */
    label?: string;
    /**
     * Measured size of the rendered node content. The layout falls back to default sizes while this is
     * unset; the graph stays hidden until every node has reported a dimension (see {@link DagGraphComponent}).
     */
    dimension?: GraphNodeDimension;
}

/** A directed edge as provided by the consumer. Edges whose `source`/`target` do not match a node id are skipped. */
export interface DagGraphEdge {
    /** Stable, unique id (also used as the dagre multigraph edge name, so parallel edges are kept apart). */
    id: string;
    /** Id of the node the edge starts at. */
    source: string;
    /** Id of the node the edge points to. */
    target: string;
    /** Optional label (rendering is up to the projected edge template). */
    label?: string;
    /** Arbitrary consumer payload carried through to the layout edge untouched (e.g. the relation id for click handling). */
    data?: unknown;
}

/** A node after layout: the original node plus its resolved size, center position, and SVG transform. */
export interface DagGraphLayoutNode<T extends DagGraphNode = DagGraphNode> {
    /** The original node object passed into the graph, untouched. */
    node: T;
    /** Resolved dimension (measured, or the default until measurement arrives). */
    dimension: GraphNodeDimension;
    /** Center position assigned by the layout, in graph coordinates. */
    position: GraphPosition;
    /** SVG `transform` that places the node group at its top-left corner (`position` minus half the dimension). */
    transform: string;
}

/** An edge after layout: the original edge plus its routed points, SVG path, and label-path metadata. */
export interface DagGraphLayoutEdge<T extends DagGraphEdge = DagGraphEdge> {
    /** The original edge object passed into the graph, untouched. */
    edge: T;
    /** Polyline the dagre layout routed the edge through, in graph coordinates. */
    points: GraphPosition[];
    /** SVG path string through {@link points} (a `curveBundle` spline), for the visible edge. */
    path: string;
    /**
     * Path string to attach edge labels to. Identical to {@link path} for left-to-right edges; for
     * right-to-left edges it runs through the reversed points so `<textPath>` labels read left-to-right.
     */
    textPath: string;
    /** `dominant-baseline` that keeps the label above the line for both label-path directions. */
    dominantBaseline: 'text-before-edge' | 'text-after-edge';
}

/** The full computed layout: positioned nodes, routed edges, and the overall graph extent in px. */
export interface DagGraphLayout {
    nodes: DagGraphLayoutNode[];
    edges: DagGraphLayoutEdge[];
    /** Total layout width in px (0 for an empty graph). */
    width: number;
    /** Total layout height in px (0 for an empty graph). */
    height: number;
}
