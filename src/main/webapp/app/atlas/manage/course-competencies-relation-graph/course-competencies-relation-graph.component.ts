import { Component, computed, effect, input, model, output, signal } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faFileImport } from '@fortawesome/free-solid-svg-icons';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyRelationDTO, CourseCompetency } from 'app/atlas/shared/entities/competency.model';

import { Edge, NgxGraphModule, Node } from '@swimlane/ngx-graph';
import { Subject } from 'rxjs';
import { SizeUpdate } from 'app/atlas/manage/competency-node/competency-node.component';
import { CourseCompetencyRelationNodeComponent } from 'app/atlas/manage/course-competency-relation-node/course-competency-relation-node.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-course-competencies-relation-graph',
    imports: [FontAwesomeModule, NgbAccordionModule, NgxGraphModule, CourseCompetencyRelationNodeComponent, ArtemisTranslatePipe],
    templateUrl: './course-competencies-relation-graph.component.html',
    styleUrl: './course-competencies-relation-graph.component.scss',
})
export class CourseCompetenciesRelationGraphComponent {
    protected readonly faFileImport = faFileImport;

    readonly courseCompetencies = input.required<CourseCompetency[]>();
    readonly relations = input.required<CompetencyRelationDTO[]>();

    readonly selectedRelationId = model.required<number | undefined>();

    readonly onCourseCompetencySelection = output<number>();

    readonly update$ = new Subject<boolean>();
    readonly center$ = new Subject<boolean>();

    readonly nodes = signal<Node[]>([]);

    readonly edges = computed<Edge[]>(() => {
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
            this.nodes.set(
                this.courseCompetencies().map(
                    (courseCompetency): Node => ({
                        id: courseCompetency.id!.toString(),
                        label: courseCompetency.title,
                        data: {
                            id: courseCompetency.id,
                            type: courseCompetency.type,
                        },
                    }),
                ),
            );

            // Trigger graph update when nodes change
            this.update$.next(true);
        });

        // Trigger graph update and center when edges (relations) change
        effect(() => {
            // Access edges to track changes
            this.edges();

            // Delay to ensure graph has rendered paths before textPath references them
            // Multiple updates ensure proper rendering of textPath elements
            setTimeout(() => {
                this.update$.next(true);
            }, 50);
            setTimeout(() => {
                this.center$.next(true);
                this.update$.next(true);
            }, 150);
            setTimeout(() => {
                this.update$.next(true);
            }, 300);
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
