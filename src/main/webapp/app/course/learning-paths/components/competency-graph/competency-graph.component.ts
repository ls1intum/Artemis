import { AfterViewInit, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Layout, NgxGraphModule, NgxGraphZoomOptions } from '@swimlane/ngx-graph';
import { Subject } from 'rxjs';
import { CompetencyGraphDTO, NodeType } from 'app/entities/competency/learning-path.model';
import { CompetencyNodeComponent, SizeUpdate } from 'app/course/learning-paths/components/competency-node/competency-node.component';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-competency-graph',
    standalone: true,
    imports: [CompetencyNodeComponent, NgxGraphModule],
    templateUrl: './competency-graph.component.html',
    styleUrl: './competency-graph.component.scss',
})
export class CompetencyGraphComponent implements OnInit, AfterViewInit {
    protected readonly NodeType = NodeType;

    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);

    learningPathId = input.required<number>();
    readonly isLoading = signal<boolean>(false);

    private readonly competencyGraph = signal<CompetencyGraphDTO>({ nodes: [], edges: [] });
    readonly nodes = computed(() => this.competencyGraph().nodes);
    readonly edges = computed(() => this.competencyGraph().edges);

    readonly layout = signal<string | Layout>('dagreCluster');
    readonly update$: Subject<boolean> = new Subject<boolean>();
    readonly center$: Subject<boolean> = new Subject<boolean>();
    readonly zoomToFit$: Subject<NgxGraphZoomOptions> = new Subject<NgxGraphZoomOptions>();

    ngOnInit(): void {
        this.loadCompetencyGraph(this.learningPathId());
    }

    ngAfterViewInit(): void {
        this.zoomToFit$.next({ autoCenter: true });
    }

    async loadCompetencyGraph(learningPathId: number) {
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
