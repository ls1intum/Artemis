import { Component, computed, inject, input, model, signal } from '@angular/core';
import { CompetencyRelationDTO, CompetencyRelationType, CourseCompetency } from 'app/entities/competency.model';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { AlertService } from 'app/core/util/alert.service';

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
        return this.courseCompetencies()
            .filter(({ id }) => id !== headCompetencyId) // Exclude the head itself
            .filter(({ id }) => {
                const newRelation: CompetencyRelationDTO = {
                    headCompetencyId: headCompetencyId,
                    tailCompetencyId: id,
                    relationType: relationType,
                };
                const adjacencyMap = this.getAdjacencyMap(this.relations().concat(newRelation));
                return !this.hasCycle(adjacencyMap);
            });
    }

    /**
     * Function to build the adjacency list for the current relations
     * @private
     *
     * @returns The adjacency list for the current relations
     */
    private getAdjacencyMap(relations: CompetencyRelationDTO[]): Map<number, number[]> {
        const adjacencyMap: Map<number, number[]> = new Map();
        const matchesRelations: CompetencyRelationDTO[] = [];
        relations.forEach((relation) => {
            if (!adjacencyMap.has(relation.headCompetencyId!)) {
                adjacencyMap.set(relation.headCompetencyId!, []);
            }
            if (relation.relationType == CompetencyRelationType.MATCHES) {
                matchesRelations.push(relation);
            }
            // '+' used in push() is necessary to require the value to be a number
            adjacencyMap.get(relation.headCompetencyId!)!.push(+relation.tailCompetencyId!);
        });
        // merge matches relations into the adjacency map
        matchesRelations.forEach((relation) => {
            adjacencyMap.set(relation.headCompetencyId!, this.mergeMatchesTailRelations(adjacencyMap, relation));
        });
        matchesRelations.forEach((relation) => {
            const headRelations = this.relations().filter(({ tailCompetencyId }) => tailCompetencyId == relation.headCompetencyId);
            headRelations?.forEach((headRelation) => {
                if (headRelation.relationType != CompetencyRelationType.MATCHES) {
                    const headRelationIds = adjacencyMap.get(headRelation.headCompetencyId!)!;
                    adjacencyMap.get(headRelation.headCompetencyId!)!.push(...[relation.tailCompetencyId!, ...headRelationIds]);
                }
            });
        });
        console.log(adjacencyMap);
        return adjacencyMap;
    }

    private mergeMatchesTailRelations(adjacencyMap: Map<number, number[]>, relation: CompetencyRelationDTO): number[] {
        const existingRelations = adjacencyMap.get(relation.headCompetencyId!)!;
        if (relation.relationType != CompetencyRelationType.MATCHES) {
            return existingRelations;
        } else {
            const tailRelations = this.relations().filter(({ headCompetencyId }) => headCompetencyId == relation.tailCompetencyId);
            if (tailRelations.length > 0) {
                let tailRelationIds = new Set<number>();
                tailRelations.forEach((tailRelation) => {
                    tailRelationIds = new Set<number>([...tailRelationIds, ...this.mergeMatchesTailRelations(adjacencyMap, tailRelation)]);
                });
                return [...existingRelations, ...tailRelationIds];
            }
            return existingRelations;
        }
    }

    private hasCycle(adjacencyMap: Map<number, number[]>): boolean {
        const visited = new Set<number>();
        const recursionStack = new Set<number>();

        for (const [node] of adjacencyMap) {
            if (this.hasCycleUtil(adjacencyMap, node, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    private hasCycleUtil(adjacencyMap: Map<number, number[]>, node: number, visited: Set<number>, recursionStack: Set<number>) {
        if (recursionStack.has(node)) {
            // Cycle detected
            return true;
        }
        if (visited.has(node)) {
            // Already visited
            return false;
        }
        //mark node as visited and add to recursion stack
        visited.add(node);
        recursionStack.add(node);

        // recur for all adjacent nodes
        const neighbors = adjacencyMap.get(node) || [];
        for (const neighbor of neighbors) {
            if (this.hasCycleUtil(adjacencyMap, neighbor, visited, recursionStack)) {
                return true;
            }
        }
        recursionStack.delete(node);
        return false;
    }
}
