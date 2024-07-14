import { AfterViewInit, Component, InputSignal, OnInit, Signal, WritableSignal, computed, inject, input, signal } from '@angular/core';
import { Layout, NgxGraphModule, NgxGraphZoomOptions } from '@swimlane/ngx-graph';
import { Subject } from 'rxjs';
import { CompetencyGraphDTO, CompetencyGraphEdgeDTO, CompetencyGraphNodeDTO, NodeType } from 'app/entities/competency/learning-path.model';
import { CompetencyNodeComponent, SizeUpdate } from 'app/course/learning-paths/components/competency-node/competency-node.component';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-competency-graph',
    standalone: true,
    imports: [CompetencyNodeComponent, NgxGraphModule, ArtemisSharedModule],
    templateUrl: './competency-graph.component.html',
    styleUrl: './competency-graph.component.scss',
})
export class CompetencyGraphComponent implements OnInit, AfterViewInit {
    protected readonly NodeType = NodeType;

    private readonly learningPathApiService: LearningPathApiService = inject(LearningPathApiService);
    private readonly alertService: AlertService = inject(AlertService);

    learningPathId: InputSignal<number> = input.required<number>();
    readonly isLoading: WritableSignal<boolean> = signal<boolean>(false);

    private readonly competencyGraph: WritableSignal<CompetencyGraphDTO> = signal<CompetencyGraphDTO>({ nodes: [], edges: [] });
    readonly nodes: Signal<CompetencyGraphNodeDTO[]> = computed(() => this.competencyGraph().nodes);
    readonly edges: Signal<CompetencyGraphEdgeDTO[]> = computed(() => this.competencyGraph().edges?.map((edge) => ({ ...edge, id: `edge-${edge.id}` })) || []);

    readonly layout: WritableSignal<string | Layout> = signal('dagreCluster');
    readonly update$: Subject<boolean> = new Subject<boolean>();
    readonly center$: Subject<boolean> = new Subject<boolean>();
    readonly zoomToFit$: Subject<NgxGraphZoomOptions> = new Subject<NgxGraphZoomOptions>();

    ngOnInit(): void {
        this.loadCompetencyGraph(this.learningPathId());
    }

    ngAfterViewInit(): void {
        this.zoomToFit$.next({ autoCenter: true });
    }

    async loadCompetencyGraph(learningPathId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const competencyGraph = await this.learningPathApiService.getLearningPathCompetencyGraph(learningPathId);
            this.competencyGraph.set(competencyGraph);
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLoading.set(false);
        }
    }

    setNodeDimension(sizeUpdate: SizeUpdate): void {
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
