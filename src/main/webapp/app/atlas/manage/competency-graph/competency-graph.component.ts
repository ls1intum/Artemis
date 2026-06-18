import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CompetencyGraphDTO } from 'app/atlas/shared/entities/learning-path.model';
import { CompetencyNodeComponent, SizeUpdate } from 'app/atlas/manage/competency-node/competency-node.component';
import { DagGraphComponent } from 'app/atlas/shared/dag-graph/dag-graph.component';

@Component({
    selector: 'jhi-competency-graph',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [CompetencyNodeComponent, DagGraphComponent, TranslateDirective],
    templateUrl: './competency-graph.component.html',
    styleUrl: './competency-graph.component.scss',
})
export class CompetencyGraphComponent {
    readonly competencyGraph = input.required<CompetencyGraphDTO>();

    private readonly internalCompetencyGraph = signal<CompetencyGraphDTO>({
        nodes: [],
        edges: [],
    });
    readonly nodes = computed(() => this.internalCompetencyGraph().nodes || []);
    readonly edges = computed(() => {
        return (
            this.internalCompetencyGraph().edges?.map((edge) => ({
                ...edge,
                id: `edge-${edge.id}`,
            })) || []
        );
    });

    constructor() {
        effect(() => this.internalCompetencyGraph.set(this.competencyGraph()));
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
