/**
 * Minimal graph model for the in-house {@link DagGraphComponent}.
 * Replaces the types previously imported from @swimlane/ngx-graph.
 */

export interface GraphNodeDimension {
    width: number;
    height: number;
}

export interface GraphPosition {
    x: number;
    y: number;
}

export interface DagGraphNode {
    id: string;
    label?: string;
    /** Measured size of the rendered node content; layout falls back to defaults until set. */
    dimension?: GraphNodeDimension;
}

export interface DagGraphEdge {
    id: string;
    source: string;
    target: string;
    label?: string;
    data?: unknown;
}

export interface DagGraphLayoutNode<T extends DagGraphNode = DagGraphNode> {
    /** The original node object passed into the graph, untouched. */
    node: T;
    /** Resolved dimension (measured, or the default until measurement arrives). */
    dimension: GraphNodeDimension;
    /** Center position assigned by the layout. */
    position: GraphPosition;
    /** SVG transform placing the node's top-left corner. */
    transform: string;
}

export interface DagGraphLayoutEdge<T extends DagGraphEdge = DagGraphEdge> {
    /** The original edge object passed into the graph, untouched. */
    edge: T;
    points: GraphPosition[];
    /** SVG path through the layout points (curveBundle). */
    path: string;
    /** Path to attach edge labels to; reversed for right-to-left edges so labels read left-to-right. */
    textPath: string;
    dominantBaseline: 'text-before-edge' | 'text-after-edge';
}

export interface DagGraphLayout {
    nodes: DagGraphLayoutNode[];
    edges: DagGraphLayoutEdge[];
    width: number;
    height: number;
}
