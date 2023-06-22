import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import { Competency, CompetencyRelation, CompetencyRelationError, CourseCompetencyProgress, getIcon, getIconTooltip } from 'app/entities/competency.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { filter, finalize, map, switchMap } from 'rxjs/operators';
import { onError } from 'app/shared/util/global.utils';
import { Subject, forkJoin } from 'rxjs';
import { faPencilAlt, faPlus, faTimes } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { PrerequisiteImportComponent } from 'app/course/competencies/competency-management/prerequisite-import.component';
import { ClusterNode, Edge, Node } from '@swimlane/ngx-graph';
import { AccountService } from 'app/core/auth/account.service';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { CompetencyImportComponent } from 'app/course/competencies/competency-management/competency-import.component';

@Component({
    selector: 'jhi-competency-management',
    templateUrl: './competency-management.component.html',
    styleUrls: ['./competency-management.component.scss'],
})
export class CompetencyManagementComponent implements OnInit, OnDestroy {
    courseId: number;
    isLoading = false;
    competencies: Competency[] = [];
    prerequisites: Competency[] = [];

    showRelations = false;
    tailCompetency?: number;
    headCompetency?: number;
    relationType?: string;
    nodes: Node[] = [];
    edges: Edge[] = [];
    clusters: ClusterNode[] = [];
    competencyRelationError = CompetencyRelationError;
    relationError: CompetencyRelationError = CompetencyRelationError.NONE;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    documentationType = DocumentationType.Competencies;

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faPencilAlt = faPencilAlt;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private accountService: AccountService,
        private competencyService: CompetencyService,
        private alertService: AlertService,
        private modalService: NgbModal,
    ) {}

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    ngOnInit(): void {
        this.showRelations = this.accountService.isAdmin(); // beta feature
        this.activatedRoute.parent!.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            if (this.courseId) {
                this.loadData();
            }
        });
    }

    validate(): void {
        if (this.headCompetency && this.tailCompetency && this.relationType && this.headCompetency === this.tailCompetency) {
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
        this.relationError = CompetencyRelationError.NONE;
    }

    getErrorMessage(error: CompetencyRelationError): string {
        switch (error) {
            case CompetencyRelationError.CIRCULAR: {
                return 'artemisApp.competency.relation.createsCircularRelation';
            }
            case CompetencyRelationError.EXISTING: {
                return 'artemisApp.competency.relation.relationAlreadyExists';
            }
            case CompetencyRelationError.SELF: {
                return 'artemisApp.competency.relation.selfRelation';
            }
            case CompetencyRelationError.NONE: {
                throw new TypeError('There is no error message if there is no error.');
            }
        }
    }

    identify(index: number, competency: Competency) {
        return `${index}-${competency.id}`;
    }

    deleteCompetency(competencyId: number) {
        this.competencyService.delete(competencyId, this.courseId).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.loadData();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    removePrerequisite(competencyId: number) {
        this.competencyService.removePrerequisite(competencyId, this.courseId).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.loadData();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    loadData() {
        this.isLoading = true;
        this.competencyService
            .getAllPrerequisitesForCourse(this.courseId)
            .pipe(map((response: HttpResponse<Competency[]>) => response.body!))
            .subscribe({
                next: (competencies) => {
                    this.prerequisites = competencies;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        this.competencyService
            .getAllForCourse(this.courseId)
            .pipe(
                switchMap((res) => {
                    this.competencies = res.body!;

                    this.nodes = this.competencies.map(
                        (competency): Node => ({
                            id: `${competency.id}`,
                            label: competency.title,
                        }),
                    );

                    const relationsObservable = this.competencies.map((lg) => {
                        return this.competencyService.getCompetencyRelations(lg.id!, this.courseId);
                    });

                    const progressObservable = this.competencies.map((lg) => {
                        return this.competencyService.getCourseProgress(lg.id!, this.courseId);
                    });

                    return forkJoin([forkJoin(relationsObservable), forkJoin(progressObservable)]);
                }),
            )
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: ([competencyRelations, competencyProgressResponses]) => {
                    const relations = [
                        ...competencyRelations
                            .flatMap((response) => response.body!)
                            .reduce((a, c) => {
                                a.set(c.id, c);
                                return a;
                            }, new Map())
                            .values(),
                    ];
                    this.edges = relations.map(
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
                    this.clusters = relations
                        .filter((relation) => relation.type === 'CONSECUTIVE')
                        .map(
                            (relation): ClusterNode => ({
                                id: `cluster${relation.id}`,
                                label: relation.type,
                                childNodeIds: [`${relation.tailCompetency?.id}`, `${relation.headCompetency?.id}`],
                                data: {
                                    id: relation.id,
                                },
                            }),
                        );

                    for (const competencyProgressResponse of competencyProgressResponses) {
                        const courseCompetencyProgress: CourseCompetencyProgress = competencyProgressResponse.body!;
                        this.competencies.find((lg) => lg.id === courseCompetencyProgress.competencyId)!.courseProgress = courseCompetencyProgress;
                    }
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    /**
     * Opens a modal for adding a prerequisite to the current course.
     */
    openPrerequisiteSelectionModal() {
        const modalRef = this.modalService.open(PrerequisiteImportComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.disabledIds = this.competencies.concat(this.prerequisites).map((competency) => competency.id);
        modalRef.result.then((result: Competency) => {
            this.competencyService
                .addPrerequisite(result.id!, this.courseId)
                .pipe(
                    filter((res: HttpResponse<Competency>) => res.ok),
                    map((res: HttpResponse<Competency>) => res.body),
                )
                .subscribe({
                    next: (res: Competency) => {
                        this.prerequisites.push(res);
                    },
                    error: (res: HttpErrorResponse) => onError(this.alertService, res),
                });
        });
    }

    /**
     * Opens a modal for selecting a competency to import to the current course.
     */
    openImportModal() {
        const modalRef = this.modalService.open(CompetencyImportComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.disabledIds = this.competencies.concat(this.prerequisites).map((competency) => competency.id);
        modalRef.result.then((selectedCompetency: Competency) => {
            this.competencyService
                .import(selectedCompetency, this.courseId)
                .pipe(
                    filter((res: HttpResponse<Competency>) => res.ok),
                    map((res: HttpResponse<Competency>) => res.body),
                )
                .subscribe({
                    next: (res: Competency) => {
                        this.competencies.push(res);
                    },
                    error: (res: HttpErrorResponse) => onError(this.alertService, res),
                });
        });
    }

    createRelation() {
        if (this.relationError !== CompetencyRelationError.NONE) {
            switch (this.relationError) {
                case CompetencyRelationError.CIRCULAR: {
                    throw new TypeError('Creation of circular relations is not allowed.');
                }
                case CompetencyRelationError.EXISTING: {
                    throw new TypeError('Creation of an already existing relation is not allowed.');
                }
                case CompetencyRelationError.SELF: {
                    throw new TypeError('Creation of a self relation is not allowed.');
                }
            }
        }
        this.competencyService
            .createCompetencyRelation(this.tailCompetency!, this.headCompetency!, this.relationType!, this.courseId)
            .pipe(
                filter((res: HttpResponse<CompetencyRelation>) => res.ok),
                map((res: HttpResponse<CompetencyRelation>) => res.body),
            )
            .subscribe({
                next: () => {
                    this.loadData();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    removeRelation(edge: Edge) {
        this.competencyService.removeCompetencyRelation(Number(edge.source), Number(edge.data.id), this.courseId).subscribe({
            next: () => {
                this.loadData();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    private containsCircularRelation(): boolean {
        if (this.headCompetency !== this.tailCompetency) {
            return !!(
                this.tailCompetency &&
                this.headCompetency &&
                this.relationType &&
                this.doesCreateCircularRelation(this.nodes, this.edges, {
                    source: this.tailCompetency! + '',
                    target: this.headCompetency! + '',
                    label: this.relationType!,
                } as Edge)
            );
        } else {
            return false;
        }
    }

    private doesRelationAlreadyExist(): boolean {
        return this.edges.find((edge) => edge.source === this.tailCompetency?.toString() && edge.target === this.headCompetency?.toString()) !== undefined;
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
export class Vertex {
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
export class Graph {
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
            if (neighbor.isBeingVisited()) {
                // backward edge exists
                return true;
            } else if (!neighbor.isVisited() && this.vertexHasCycle(neighbor)) {
                return true;
            }
        }

        sourceVertex.setBeingVisited(false);
        sourceVertex.setVisited(true);
        return false;
    }
}
