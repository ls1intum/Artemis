import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import { NgxGraphModule, NgxGraphZoomOptions } from '@swimlane/ngx-graph';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { Subject } from 'rxjs';
import { CompetencyGraphDTO } from 'app/atlas/shared/entities/learning-path.model';
import { CompetencyNodeComponent, SizeUpdate } from 'app/atlas/manage/competency-node/competency-node.component';

@Component({
    selector: 'jhi-competency-graph',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [CompetencyNodeComponent, NgxGraphModule, TranslateDirective],
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
        return this.internalCompetencyGraph().edges?.map((edge) => Object.assign({}, edge, { id: `edge-${edge.id}` })) || [];
    });

    readonly update$ = new Subject<boolean>();
    readonly center$ = new Subject<boolean>();
    readonly zoomToFit$ = new Subject<NgxGraphZoomOptions>();

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
