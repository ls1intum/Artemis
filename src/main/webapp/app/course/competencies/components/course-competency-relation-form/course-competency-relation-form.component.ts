import { Component, computed, inject, input, model, signal } from '@angular/core';
import { CompetencyRelationDTO, CompetencyRelationType, CourseCompetency } from 'app/entities/competency.model';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { AlertService } from 'app/core/util/alert.service';

type AdjacencyMap = { [key in CompetencyRelationType]?: Map<number, number[]> };

@Component({
    selector: 'jhi-course-competency-relation-form',
    standalone: true,
    imports: [ArtemisSharedCommonModule],
    templateUrl: './course-competency-relation-form.component.html',
    styleUrl: './course-competency-relation-form.component.scss',
})
export class CourseCompetencyRelationFormComponent {
    protected readonly competencyRelationType = CompetencyRelationType;

    private readonly courseCompetencyApiService = inject(CourseCompetencyApiService);
    private readonly alertService = inject(AlertService);

    readonly courseId = input.required<number>();
    readonly courseCompetencies = input.required<CourseCompetency[]>();
    readonly relations = model.required<CompetencyRelationDTO[]>();

    protected readonly headCompetencyId = signal<number | undefined>(undefined);
    protected readonly tailCompetencyId = model<number | undefined>(undefined);
    protected readonly relationType = model<CompetencyRelationType | undefined>(undefined);

    protected readonly isLoading = signal<boolean>(false);

    protected readonly relationAlreadyExists = computed(() =>
        this.relations().some((relation) => relation.headCompetencyId == this.headCompetencyId() && relation.tailCompetencyId == this.tailCompetencyId()),
    );
    protected readonly relationTypeAlreadyExists = computed(() =>
        this.relations().some(
            (relation) =>
                relation.headCompetencyId == this.headCompetencyId() && relation.tailCompetencyId == this.tailCompetencyId() && relation.relationType == this.relationType(),
        ),
    );

    protected readonly selectableTailCourseCompetencies = computed(() => {
        if (this.headCompetencyId() && this.relationType()) {
            return this.getSelectableTailCompetencies(this.headCompetencyId()!, this.relationType()!);
        }
        return this.courseCompetencies();
    });

    public selectRelation(relationId: number): void {
        const relation = this.relations().find(({ id }) => id === relationId);
        this.headCompetencyId.set(relation?.headCompetencyId);
        this.tailCompetencyId.set(relation?.tailCompetencyId);
        this.relationType.set(relation?.relationType);
    }

    public selectCourseCompetency(courseCompetencyId: number): void {
        if (!this.headCompetencyId() || this.tailCompetencyId()) {
            this.headCompetencyId.set(courseCompetencyId);
            this.tailCompetencyId.set(undefined);
            this.relationType.set(undefined);
        } else {
            if (this.selectableTailCourseCompetencies().find(({ id }) => id === courseCompetencyId)) {
                this.tailCompetencyId.set(courseCompetencyId);
            }
        }
    }

    protected selectHeadCourseCompetency(event: Event) {
        const target = event.target as HTMLSelectElement;
        this.headCompetencyId.set(Number(target.value));
        this.tailCompetencyId.set(undefined);
    }

    protected resetForm() {
        this.headCompetencyId.set(undefined);
        this.tailCompetencyId.set(undefined);
        this.relationType.set(undefined);
    }

    protected async createRelation(): Promise<void> {
        try {
            this.isLoading.set(true);
            const courseCompetencyRelation = await this.courseCompetencyApiService.createCourseCompetencyRelation(this.courseId(), {
                headCompetencyId: this.headCompetencyId(),
                tailCompetencyId: this.tailCompetencyId(),
                relationType: this.relationType(),
            });
            this.relations.update((relations) => [...relations, courseCompetencyRelation]);
            this.resetForm();
        } catch (error) {
            this.alertService.error(error.message);
        } finally {
            this.isLoading.set(false);
        }
    }

    protected async deleteRelation(): Promise<void> {
        try {
            this.isLoading.set(true);
            const deletedRelation = this.relations().find(
                ({ headCompetencyId, tailCompetencyId, relationType }) =>
                    headCompetencyId == this.headCompetencyId() && tailCompetencyId == this.tailCompetencyId() && relationType === this.relationType(),
            );
            if (deletedRelation) {
                await this.courseCompetencyApiService.deleteCourseCompetencyRelation(this.courseId(), deletedRelation.id!);
                this.relations.update((relations) => relations.filter(({ id }) => id !== deletedRelation.id));
                this.resetForm();
            }
        } catch (error) {
            this.alertService.error(error.message);
        } finally {
            this.isLoading.set(false);
        }
    }

    /**
     * Function to get the selectable tail competencies for the given head competency and relation type
     * @param headCompetencyId The selected head competency id
     * @param relationType The selected relation type
     * @private
     *
     * @returns The selectable tail competencies
     */
    private getSelectableTailCompetencies(headCompetencyId: number, relationType: CompetencyRelationType): CourseCompetency[] {
        const courseCompetencies = this.courseCompetencies();
        const adjacencyMap = this.getAdjacencyMap();
        return courseCompetencies
            .filter(({ id }) => id !== headCompetencyId) // Exclude the head itself
            .filter(
                ({ id }) =>
                    // only extends and assumes relations are considered when checking for circles because only they don't make sense
                    relationType === CompetencyRelationType.MATCHES ||
                    (!this.hasRelation(adjacencyMap, id!, headCompetencyId, CompetencyRelationType.EXTENDS) &&
                        !this.hasRelation(adjacencyMap, id!, headCompetencyId, CompetencyRelationType.ASSUMES)),
            );
    }

    /**
     * Function to build the adjacency list for the current relations
     * @private
     *
     * @returns The adjacency list for the current relations
     */
    private getAdjacencyMap(): AdjacencyMap {
        const adjacencyMap: AdjacencyMap = {
            ASSUMES: new Map(),
            EXTENDS: new Map(),
            MATCHES: new Map(),
        };
        this.relations().forEach((relation) => {
            const relationType = relation.relationType!;
            if (!adjacencyMap[relationType]!.has(relation.headCompetencyId!)) {
                adjacencyMap[relationType]!.set(relation.headCompetencyId!, []);
            }
            adjacencyMap[relationType]!.get(relation.headCompetencyId!)!.push(relation.tailCompetencyId!);
        });
        return adjacencyMap;
    }

    /**
     * Function to check if there's a path from 'headCompetencyId' to 'tailCompetencyId' using DFS (Depth First Search)
     * @param adjacencyMap The adjacency list for the current relations
     * @param head The selected head competency id
     * @param tail The selected tail competency id
     * @param relationType The selected relation type
     * @param visited The set of visited nodes
     * @private
     *
     * @returns True if there's a path from 'headCompetencyId' to 'tailCompetencyId', false otherwise
     */
    private hasRelation(adjacencyMap: AdjacencyMap, head: number, tail: number, relationType: CompetencyRelationType, visited: Set<number> = new Set()): boolean {
        if (head === tail) {
            return true;
        }
        if (visited.has(head)) {
            return false;
        }
        visited.add(head);

        const neighbors = adjacencyMap[relationType]?.get(head) || [];
        for (const neighbor of neighbors) {
            if (this.hasRelation(adjacencyMap, neighbor, tail, relationType, visited)) {
                return true;
            }
        }
        return false;
    }
}
