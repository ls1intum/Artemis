import { Component, computed, effect, input, Signal, signal, WritableSignal } from '@angular/core';
import { Layout, NgxGraphModule, NgxGraphZoomOptions } from '@swimlane/ngx-graph';
import { Subject } from 'rxjs';
import { CompetencyGraphDTO, CompetencyGraphEdgeDTO, CompetencyGraphNodeDTO, NodeType } from 'app/entities/competency/learning-path.model';
import { CompetencyNodeComponent, SizeUpdate } from 'app/course/learning-paths/components/competency-node/competency-node.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-competency-graph',
    standalone: true,
    imports: [CompetencyNodeComponent, NgxGraphModule, ArtemisSharedModule],
    templateUrl: './competency-graph.component.html',
    styleUrl: './competency-graph.component.scss',
})
export class CompetencyGraphComponent {
    protected readonly NodeType = NodeType;

    readonly competencyGraph = input.required<CompetencyGraphDTO>();

    private readonly internalCompetencyGraph: WritableSignal<CompetencyGraphDTO> = signal<CompetencyGraphDTO>({ nodes: [], edges: [] });
    readonly nodes: Signal<CompetencyGraphNodeDTO[]> = computed(() => this.internalCompetencyGraph().nodes);
    readonly edges: Signal<CompetencyGraphEdgeDTO[]> = computed(
        () =>
            this.internalCompetencyGraph().edges?.map((edge) => ({
                ...edge,
                id: `edge-${edge.id}`,
            })) || [],
    );

    readonly layout: WritableSignal<string | Layout> = signal('dagreCluster');
    readonly update$: Subject<boolean> = new Subject<boolean>();
    readonly center$: Subject<boolean> = new Subject<boolean>();
    readonly zoomToFit$: Subject<NgxGraphZoomOptions> = new Subject<NgxGraphZoomOptions>();

    constructor() {
        effect(() => this.internalCompetencyGraph.set(this.competencyGraph()), { allowSignalWrites: true });
    }

    setNodeDimension(sizeUpdate: SizeUpdate): void {
        this.internalCompetencyGraph.update(({ nodes, edges }) => {
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
