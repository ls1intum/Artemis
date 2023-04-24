import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { AlertService } from 'app/core/util/alert.service';
import { CourseLearningGoalProgress, LearningGoal, LearningGoalRelation, getIcon, getIconTooltip } from 'app/entities/learningGoal.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { filter, finalize, map, switchMap } from 'rxjs/operators';
import { onError } from 'app/shared/util/global.utils';
import { Subject, forkJoin } from 'rxjs';
import { faPencilAlt, faPlus, faTimes } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { PrerequisiteImportComponent } from 'app/course/learning-goals/learning-goal-management/prerequisite-import.component';
import { ClusterNode, Edge, Node } from '@swimlane/ngx-graph';
import { AccountService } from 'app/core/auth/account.service';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { CompetencyImportComponent } from 'app/course/learning-goals/learning-goal-management/competency-import.component';

@Component({
    selector: 'jhi-learning-goal-management',
    templateUrl: './learning-goal-management.component.html',
    styleUrls: ['./learning-goal-management.component.scss'],
})
export class LearningGoalManagementComponent implements OnInit, OnDestroy {
    courseId: number;
    isLoading = false;
    learningGoals: LearningGoal[] = [];
    prerequisites: LearningGoal[] = [];

    showRelations = false;
    tailLearningGoal?: number;
    headLearningGoal?: number;
    relationType?: string;
    nodes: Node[] = [];
    edges: Edge[] = [];
    clusters: ClusterNode[] = [];
    containsCircularRelation = false;

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
        private learningGoalService: LearningGoalService,
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

    updateContainsCircularRelation() {
        if (this.headLearningGoal !== this.tailLearningGoal) {
            this.containsCircularRelation = !!(
                this.tailLearningGoal &&
                this.headLearningGoal &&
                this.relationType &&
                this.doesCreateCircularRelation(this.nodes, this.edges, {
                    source: this.tailLearningGoal! + '',
                    target: this.headLearningGoal! + '',
                    label: this.relationType!,
                } as Edge)
            );
        }
    }

    identify(index: number, learningGoal: LearningGoal) {
        return `${index}-${learningGoal.id}`;
    }

    deleteLearningGoal(learningGoalId: number) {
        this.learningGoalService.delete(learningGoalId, this.courseId).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.loadData();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    removePrerequisite(learningGoalId: number) {
        this.learningGoalService.removePrerequisite(learningGoalId, this.courseId).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.loadData();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    loadData() {
        this.isLoading = true;
        this.learningGoalService
            .getAllPrerequisitesForCourse(this.courseId)
            .pipe(map((response: HttpResponse<LearningGoal[]>) => response.body!))
            .subscribe({
                next: (learningGoals) => {
                    this.prerequisites = learningGoals;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        this.learningGoalService
            .getAllForCourse(this.courseId)
            .pipe(
                switchMap((res) => {
                    this.learningGoals = res.body!;

                    this.nodes = this.learningGoals.map(
                        (learningGoal): Node => ({
                            id: `${learningGoal.id}`,
                            label: learningGoal.title,
                        }),
                    );

                    const relationsObservable = this.learningGoals.map((lg) => {
                        return this.learningGoalService.getLearningGoalRelations(lg.id!, this.courseId);
                    });

                    const progressObservable = this.learningGoals.map((lg) => {
                        return this.learningGoalService.getCourseProgress(lg.id!, this.courseId);
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
                next: ([learningGoalRelations, learningGoalProgressResponses]) => {
                    const relations = [
                        ...learningGoalRelations
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
                            source: `${relation.tailLearningGoal?.id}`,
                            target: `${relation.headLearningGoal?.id}`,
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
                                childNodeIds: [`${relation.tailLearningGoal?.id}`, `${relation.headLearningGoal?.id}`],
                                data: {
                                    id: relation.id,
                                },
                            }),
                        );

                    for (const learningGoalProgressResponse of learningGoalProgressResponses) {
                        const courseLearningGoalProgress: CourseLearningGoalProgress = learningGoalProgressResponse.body!;
                        this.learningGoals.find((lg) => lg.id === courseLearningGoalProgress.learningGoalId)!.courseProgress = courseLearningGoalProgress;
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
        modalRef.componentInstance.disabledIds = this.learningGoals.concat(this.prerequisites).map((learningGoal) => learningGoal.id);
        modalRef.result.then((result: LearningGoal) => {
            this.learningGoalService
                .addPrerequisite(result.id!, this.courseId)
                .pipe(
                    filter((res: HttpResponse<LearningGoal>) => res.ok),
                    map((res: HttpResponse<LearningGoal>) => res.body),
                )
                .subscribe({
                    next: (res: LearningGoal) => {
                        this.prerequisites.push(res);
                    },
                    error: (res: HttpErrorResponse) => onError(this.alertService, res),
                });
        });
    }

    /**
     * Opens a modal for selecting a learning goal to import to the current course.
     */
    openImportModal() {
        const modalRef = this.modalService.open(CompetencyImportComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.disabledIds = this.learningGoals.concat(this.prerequisites).map((learningGoal) => learningGoal.id);
        modalRef.result.then((selectedLearningGoal: LearningGoal) => {
            this.learningGoalService
                .import(selectedLearningGoal, this.courseId)
                .pipe(
                    filter((res: HttpResponse<LearningGoal>) => res.ok),
                    map((res: HttpResponse<LearningGoal>) => res.body),
                )
                .subscribe({
                    next: (res: LearningGoal) => {
                        this.learningGoals.push(res);
                    },
                    error: (res: HttpErrorResponse) => onError(this.alertService, res),
                });
        });
    }

    createRelation() {
        if (this.containsCircularRelation) {
            throw new TypeError('Creation of circular relations is not allowed.');
        }
        this.learningGoalService
            .createLearningGoalRelation(this.tailLearningGoal!, this.headLearningGoal!, this.relationType!, this.courseId)
            .pipe(
                filter((res: HttpResponse<LearningGoalRelation>) => res.ok),
                map((res: HttpResponse<LearningGoalRelation>) => res.body),
            )
            .subscribe({
                next: () => {
                    this.loadData();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    removeRelation(edge: Edge) {
        this.learningGoalService.removeLearningGoalRelation(Number(edge.source), Number(edge.data.id), this.courseId).subscribe({
            next: () => {
                this.loadData();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
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
                //change to set
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
