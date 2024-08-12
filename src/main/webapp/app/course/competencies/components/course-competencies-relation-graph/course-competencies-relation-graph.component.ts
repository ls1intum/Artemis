import { Component, computed, effect, input, model, signal } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faFileImport } from '@fortawesome/free-solid-svg-icons';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyRelationDTO, CourseCompetency } from 'app/entities/competency.model';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { Edge, NgxGraphModule, NgxGraphZoomOptions, Node } from '@swimlane/ngx-graph';
import { Subject } from 'rxjs';
import { SizeUpdate } from 'app/course/learning-paths/components/competency-node/competency-node.component';
import { CourseCompetenciesRelationNodeComponent } from 'app/course/competencies/components/course-competencies-relation-node/course-competencies-relation-node.component';

@Component({
    selector: 'jhi-course-competencies-relation-graph',
    standalone: true,
    imports: [FontAwesomeModule, NgbAccordionModule, NgxGraphModule, ArtemisSharedModule, CourseCompetenciesRelationNodeComponent],
    templateUrl: './course-competencies-relation-graph.component.html',
    styleUrl: './course-competencies-relation-graph.component.scss',
})
export class CourseCompetenciesRelationGraphComponent {
    protected readonly faFileImport = faFileImport;

    readonly courseCompetencies = input.required<CourseCompetency[]>();
    readonly relations = model.required<CompetencyRelationDTO[]>();

    readonly update$ = new Subject<boolean>();
    readonly center$ = new Subject<boolean>();
    readonly zoomToFit$ = new Subject<NgxGraphZoomOptions>();

    readonly nodes = signal<Node[]>([]);

    readonly edges = computed<Edge[]>(() => {
        return this.relations().map((relation) => ({
            id: `edge-${relation.id}`,
            source: `${relation.tailCompetencyId}`,
            target: `${relation.headCompetencyId}`,
            label: relation.relationType,
            data: {
                id: relation.id,
            },
        }));
    });

    constructor() {
        effect(
            () => {
                return this.nodes.set(
                    this.courseCompetencies().map(
                        (courseCompetency): Node => ({
                            id: courseCompetency.id!.toString(),
                            label: courseCompetency.title,
                            data: {
                                type: courseCompetency.type,
                            },
                        }),
                    ),
                );
            },
            { allowSignalWrites: true },
        );
    }

    setNodeDimension(sizeUpdate: SizeUpdate): void {
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
