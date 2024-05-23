import { Component, afterNextRender, computed, signal } from '@angular/core';
import { Layout, NgxGraphModule, NgxGraphZoomOptions, NodeDimension } from '@swimlane/ngx-graph';
import { Subject } from 'rxjs';
import { NodeType } from 'app/entities/competency/learning-path.model';
import { CompetencyNodeComponent, SizeUpdate } from 'app/course/learning-paths/components/competency-node/competency-node.component';

export interface CompetencyNode {
    id: string;
    label: string;
    progress: number;
    mastery: number;
    confidence: number;
    dimension?: NodeDimension;
}

export interface Edge {
    id?: string;
    source: string;
    target: string;
    type: string;
}

export interface CompetencyGraph {
    nodes: CompetencyNode[];
    edges: Edge[];
}

@Component({
    selector: 'jhi-competency-graph',
    standalone: true,
    imports: [CompetencyNodeComponent, NgxGraphModule],
    templateUrl: './competency-graph.component.html',
    styleUrl: './competency-graph.component.scss',
})
export class CompetencyGraphComponent {
    protected readonly NodeType = NodeType;

    private readonly competencyGraph = signal({
        nodes: [
            {
                id: '1',
                label: 'Variablen und Funktionen',
                mastery: 90,
            },
            {
                id: '2',
                label: 'Kontrollstrukturen',
                mastery: 40,
            },
            {
                id: '3',
                label: 'Basistypen und Operatoren',
                mastery: 20,
            },
            {
                id: '4',
                label: 'Arrays',
                mastery: 0,
            },
            {
                id: '5',
                label: 'Switche',
                mastery: 0,
            },
            {
                id: '6',
                label: 'Schleifen',
                mastery: 0,
            },
        ] as CompetencyNode[],
        edges: [
            {
                id: 'a',
                source: '1',
                target: '2',
            },
            {
                id: 'b',
                source: '1',
                target: '3',
            },
            {
                id: 'c',
                source: '3',
                target: '4',
            },
            {
                id: 'd',
                source: '3',
                target: '5',
            },
            {
                id: 'e',
                source: '4',
                target: '5',
            },
            {
                id: 'f',
                source: '2',
                target: '6',
            },
        ] as Edge[],
    } as CompetencyGraph);

    readonly nodes = computed(() => this.competencyGraph().nodes);
    readonly edges = computed(() => this.competencyGraph().edges);

    readonly layout = signal<string | Layout>('dagreCluster');
    readonly update$: Subject<boolean> = new Subject<boolean>();
    readonly center$: Subject<boolean> = new Subject<boolean>();
    readonly zoomToFit$: Subject<NgxGraphZoomOptions> = new Subject<NgxGraphZoomOptions>();

    constructor() {
        afterNextRender(() => {
            this.zoomToFit$.next({ autoCenter: true });
        });
    }

    setNodeDimension(sizeUpdate: SizeUpdate) {
        this.competencyGraph.update(({ nodes, edges }) => {
            return {
                nodes: nodes.map((node) => {
                    if (node.id === sizeUpdate.id) {
                        node.dimension = sizeUpdate.dimension;
                    }
                    return node;
                }),
                edges: edges,
            };
        });
    }
}
