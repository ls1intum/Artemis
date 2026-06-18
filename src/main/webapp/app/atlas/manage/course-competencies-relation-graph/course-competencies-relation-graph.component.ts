import { ChangeDetectionStrategy, Component, computed, effect, input, model, output, signal } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faFileImport } from '@fortawesome/free-solid-svg-icons';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyRelationDTO, CourseCompetency, CourseCompetencyGraphNode } from 'app/atlas/shared/entities/competency.model';
import { SizeUpdate } from 'app/atlas/manage/competency-node/competency-node.component';
import { CourseCompetencyRelationNodeComponent } from 'app/atlas/manage/course-competency-relation-node/course-competency-relation-node.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DagGraphComponent } from 'app/atlas/shared/dag-graph/dag-graph.component';
import { DagGraphEdge } from 'app/atlas/shared/dag-graph/dag-graph.model';

@Component({
    selector: 'jhi-course-competencies-relation-graph',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FontAwesomeModule, NgbAccordionModule, DagGraphComponent, CourseCompetencyRelationNodeComponent, ArtemisTranslatePipe],
    templateUrl: './course-competencies-relation-graph.component.html',
    styleUrl: './course-competencies-relation-graph.component.scss',
})
export class CourseCompetenciesRelationGraphComponent {
    protected readonly faFileImport = faFileImport;

    readonly courseCompetencies = input.required<CourseCompetency[]>();
    readonly relations = input.required<CompetencyRelationDTO[]>();

    readonly selectedRelationId = model.required<number | undefined>();

    readonly onCourseCompetencySelection = output<number>();

    readonly nodes = signal<CourseCompetencyGraphNode[]>([]);

    readonly edges = computed<DagGraphEdge[]>(() => {
        return this.relations().map((relation) => ({
            id: `edge-${relation.id}`,
            source: `${relation.headCompetencyId}`,
            target: `${relation.tailCompetencyId}`,
            label: relation.relationType,
            data: {
                id: relation.id,
            },
        }));
    });

    constructor() {
        effect(() => {
            return this.nodes.set(
                this.courseCompetencies().map(
                    (courseCompetency): CourseCompetencyGraphNode => ({
                        id: courseCompetency.id!.toString(),
                        label: courseCompetency.title,
                        data: {
                            id: courseCompetency.id!,
                            type: courseCompetency.type,
                        },
                    }),
                ),
            );
        });
    }

    protected selectRelation(relationId: number): void {
        this.selectedRelationId.set(relationId);
    }

    protected setNodeDimension(sizeUpdate: SizeUpdate): void {
        this.nodes.update((nodes) =>
            nodes.map((node) => {
                if (node.id === sizeUpdate.id) {
                    node.dimension = sizeUpdate.dimension;
                }
                return node;
            }),
        );
    }
}
