import { Component, EventEmitter, Output, computed, input } from '@angular/core';
import { faArrowsToEye } from '@fortawesome/free-solid-svg-icons';
import { Edge, NgxGraphZoomOptions, Node } from '@swimlane/ngx-graph';
import { CompetencyRelation, CompetencyRelationError, CompetencyRelationType, CourseCompetency } from 'app/entities/competency.model';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-competency-relation-graph',
    templateUrl: './competency-relation-graph.component.html',
    styleUrls: ['./competency-relation-graph.component.scss'],
})
export class CompetencyRelationGraphComponent {
    competencies = input<CourseCompetency[]>([]);
    relations = input<CompetencyRelation[]>([]);

    @Output() onRemoveRelation = new EventEmitter<number>();
    @Output() onCreateRelation = new EventEmitter<CompetencyRelation>();

    nodes = computed<Node[]>(() => {
        this.update$.next(true);
        return this.competencies().map((competency): Node => {
            return {
                id: `${competency.id}`,
                label: competency.title,
            };
        });
    });

    edges = computed<Edge[]>(() => {
        this.update$.next(true);
        return this.relations().map(
            (relation): Edge => ({
                id: `edge${relation.id}`,
                source: `${relation.tailCompetency?.id}`,
                target: `${relation.headCompetency?.id}`,
                label: relation.type,
                data: {
                    id: relation.id,
                },
            }),
        );
    });

    tailCompetencyId?: number;
    headCompetencyId?: number;
    relationType?: CompetencyRelationType;
    relationError?: CompetencyRelationError = undefined;
    update$: Subject<boolean> = new Subject<boolean>();
    center$: Subject<boolean> = new Subject<boolean>();
    zoomToFit$: Subject<NgxGraphZoomOptions> = new Subject<NgxGraphZoomOptions>();

    // icons
    protected readonly faArrowsToEye = faArrowsToEye;

    // constants
    protected readonly competencyRelationType = CompetencyRelationType;
    protected readonly errorMessage: Record<CompetencyRelationError, string> = {
        CIRCULAR: 'artemisApp.competency.relation.createsCircularRelation',
        EXISTING: 'artemisApp.competency.relation.relationAlreadyExists',
        SELF: 'artemisApp.competency.relation.selfRelation',
    };

    /**
     * creates a relation with the currently entered data if it would not cause an error
     */
    createRelation() {
        this.validate();
        if (this.relationError) {
            return;
        }
        const relation: CompetencyRelation = {
            tailCompetency: { id: this.tailCompetencyId },
            headCompetency: { id: this.headCompetencyId },
            type: this.relationType,
        };
        this.onCreateRelation.emit(relation);
    }

    /**
     * removes the relation
     * @param edge the edge symbolizing the relation
     */
    removeRelation(edge: Edge) {
        this.onRemoveRelation.emit(edge.data.id);
    }

    centerView() {
        this.zoomToFit$.next({ autoCenter: true });
        this.center$.next(true);
    }

    /**
     * Validates if the currently entered data would cause an error and sets relationError accordingly
     */
    validate(): void {
        if (!this.tailCompetencyId || !this.headCompetencyId || !this.relationType) {
            this.relationError = undefined;
            return;
        }
        if (this.headCompetencyId === this.tailCompetencyId) {
            this.relationError = CompetencyRelationError.SELF;
            return;
        }
        if (this.doesRelationAlreadyExist()) {
            this.relationError = CompetencyRelationError.EXISTING;
            return;
        }
        if (this.containsCircularRelation()) {
            this.relationError = CompetencyRelationError.CIRCULAR;
            return;
        }
        this.relationError = undefined;
    }

    /**
     * checks if the currently entered data is equal to an existing relation
     * @private
     */
    private doesRelationAlreadyExist(): boolean {
        return !!this.edges().find((edge) => edge.source === this.tailCompetencyId?.toString() && edge.target === this.headCompetencyId?.toString());
    }

    /**
     * Checks if the currently entered data would create a circular relation
     *
     * @private
     */
    private containsCircularRelation(): boolean {
        if (!this.tailCompetencyId || !this.headCompetencyId || !this.relationType) {
            return false;
        }
        return this.doesCreateCircularRelation(this.nodes(), this.edges(), {
            source: this.tailCompetencyId! + '',
            target: this.headCompetencyId! + '',
            label: this.relationType!,
        } as Edge);
    }

    /**
     * Checks if adding an edge would create a circular relation
     * @param   {Node[]} nodes an array of all existing nodes of a graph
     * @param   {Edge[]} edges an array of all existing edges of a graph
     * @param   {Edge} edgeToAdd the edge that you try to add to the graph
     *
     * @returns {boolean} whether or not adding the provided edge would result in a circle in the graph
     */
    private doesCreateCircularRelation(nodes: Node[], edges: Edge[], edgeToAdd: Edge): boolean {
        const edgesWithNewEdge = JSON.parse(JSON.stringify(edges));
        edgesWithNewEdge.push(edgeToAdd);
        const graph = new Graph();
        for (const node of nodes) {
            graph.addVertex(new Vertex(node.id));
        }
        for (const edge of edgesWithNewEdge) {
            const headVertex = graph.vertices.find((vertex: Vertex) => vertex.getLabel() === edge.target);
            const tailVertex = graph.vertices.find((vertex: Vertex) => vertex.getLabel() === edge.source);
            if (headVertex === undefined || tailVertex === undefined) {
                throw new TypeError('Every edge needs a source or a target.');
            }
            // only extends and assumes relations are considered when checking for circles because only they don't make sense
            // MATCHES relations are considered in the next step by merging the edges and combining the adjacencyLists
            switch (edge.label) {
                case 'EXTENDS':
                case 'ASSUMES': {
                    graph.addEdge(tailVertex, headVertex);
                    break;
                }
            }
        }
        // combine vertices that are connected through MATCHES
        for (const edge of edgesWithNewEdge) {
            if (edge.label === 'MATCHES') {
                const headVertex = graph.vertices.find((vertex: Vertex) => vertex.getLabel() === edge.target);
                const tailVertex = graph.vertices.find((vertex: Vertex) => vertex.getLabel() === edge.source);
                if (headVertex === undefined || tailVertex === undefined) {
                    throw new TypeError('Every edge needs a source or a target.');
                }
                if (headVertex.getAdjacencyList().includes(tailVertex) || tailVertex.getAdjacencyList().includes(headVertex)) {
                    return true;
                }
                // create a merged vertex
                const mergedVertex = new Vertex(tailVertex.getLabel() + ', ' + headVertex.getLabel());
                // add all neighbours to merged vertex
                mergedVertex.getAdjacencyList().push(...headVertex.getAdjacencyList());
                mergedVertex.getAdjacencyList().push(...tailVertex.getAdjacencyList());
                // update every vertex that initially had one of the two merged vertices as neighbours to now reference the merged vertex
                for (const vertex of graph.vertices) {
                    for (const adjacentVertex of vertex.getAdjacencyList()) {
                        if (adjacentVertex.getLabel() === headVertex.getLabel() || adjacentVertex.getLabel() === tailVertex.getLabel()) {
                            const index = vertex.getAdjacencyList().indexOf(adjacentVertex, 0);
                            if (index > -1) {
                                vertex.getAdjacencyList().splice(index, 1);
                            }
                            vertex.getAdjacencyList().push(mergedVertex);
                        }
                    }
                }
            }
        }
        return graph.hasCycle();
    }

    /**
     * Keeps order of elements as-is in the keyvalue pipe
     */
    keepOrder = () => {
        return 0;
    };
}

/**
 * A class that represents a vertex in a graph
 * @class
 *
 * @constructor
 *
 * @property label          a label to identify the vertex (we use the node id)
 * @property beingVisited   is the vertex the one that is currently being visited during the graph traversal
 * @property visited        has this vertex been visited before
 * @property adjacencyList  an array that contains all adjacent vertices
 */
class Vertex {
    private readonly label: string;
    private beingVisited: boolean;
    private visited: boolean;
    private readonly adjacencyList: Vertex[];

    constructor(label: string) {
        this.label = label;
        this.adjacencyList = [];
    }

    getLabel(): string {
        return this.label;
    }

    addNeighbor(adjacent: Vertex): void {
        this.adjacencyList.push(adjacent);
    }

    getAdjacencyList(): Vertex[] {
        return this.adjacencyList;
    }

    isBeingVisited(): boolean {
        return this.beingVisited;
    }

    setBeingVisited(beingVisited: boolean): void {
        this.beingVisited = beingVisited;
    }

    isVisited(): boolean {
        return this.visited;
    }

    setVisited(visited: boolean) {
        this.visited = visited;
    }
}

/**
 * A class that represents a graph
 * @class
 *
 * @constructor
 *
 * @property vertices   an array of all vertices in the graph (edges are represented by the adjacent vertices property of each vertex)
 */
class Graph {
    vertices: Vertex[];

    constructor() {
        this.vertices = [];
    }

    public addVertex(vertex: Vertex): void {
        this.vertices.push(vertex);
    }

    public addEdge(from: Vertex, to: Vertex): void {
        from.addNeighbor(to);
    }

    /**
     * Checks if the graph contains a circle
     *
     * @returns {boolean} whether or not the graph contains a circle
     */
    public hasCycle(): boolean {
        // we have to check for every vertex if it is part of a cycle in case the graph is not connected
        for (const vertex of this.vertices) {
            if (!vertex.isVisited() && this.vertexHasCycle(vertex)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a vertex is part of a circle
     *
     * @returns {boolean} whether or not the vertex is part of a circle
     */
    private vertexHasCycle(sourceVertex: Vertex): boolean {
        sourceVertex.setBeingVisited(true);

        for (const neighbor of sourceVertex.getAdjacencyList()) {
            if (neighbor.isBeingVisited() || (!neighbor.isVisited() && this.vertexHasCycle(neighbor))) {
                // backward edge exists
                return true;
            }
        }

        sourceVertex.setBeingVisited(false);
        sourceVertex.setVisited(true);
        return false;
    }
}
