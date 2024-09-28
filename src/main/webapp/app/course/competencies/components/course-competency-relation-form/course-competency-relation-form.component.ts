import { Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { CompetencyRelationDTO, CompetencyRelationType, CourseCompetency } from 'app/entities/competency.model';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-course-competency-relation-form',
    standalone: true,
    imports: [ArtemisSharedCommonModule],
    templateUrl: './course-competency-relation-form.component.html',
    styleUrl: './course-competency-relation-form.component.scss',
})
export class CourseCompetencyRelationFormComponent {
    protected readonly faSpinner = faSpinner;

    protected readonly competencyRelationType = CompetencyRelationType;

    private readonly courseCompetencyApiService = inject(CourseCompetencyApiService);
    private readonly alertService = inject(AlertService);

    readonly courseId = input.required<number>();
    readonly courseCompetencies = input.required<CourseCompetency[]>();
    readonly relations = input.required<CompetencyRelationDTO[]>();
    readonly selectedRelationId = input.required<number | undefined>();
    readonly onRelationDeselection = output<void>();

    readonly onRelationCreation = output<CompetencyRelationDTO>();
    readonly onRelationUpdate = output<CompetencyRelationDTO>();
    readonly onRelationDeletion = output<number>();

    readonly headCompetencyId = signal<number | undefined>(undefined);
    readonly tailCompetencyId = model<number | undefined>(undefined);
    readonly relationType = model<CompetencyRelationType | undefined>(undefined);

    readonly isLoading = signal<boolean>(false);

    readonly relationAlreadyExists = computed(() =>
        this.relations().some((relation) => relation.headCompetencyId == this.headCompetencyId() && relation.tailCompetencyId == this.tailCompetencyId()),
    );
    readonly relationTypeAlreadyExists = computed(() =>
        this.relations().some(
            (relation) =>
                relation.headCompetencyId == this.headCompetencyId() && relation.tailCompetencyId == this.tailCompetencyId() && relation.relationType == this.relationType(),
        ),
    );

    readonly selectableTailCourseCompetencies = computed(() => {
        if (this.headCompetencyId() && this.relationType()) {
            return this.getSelectableTailCompetencies(this.headCompetencyId()!, this.relationType()!);
        }
        return this.courseCompetencies();
    });

    private readonly currentAdjacencyMap = computed(() => this.getAdjacencyMap(this.relations()));

    constructor() {
        effect(
            () => {
                if (this.selectedRelationId()) {
                    this.selectRelation(this.selectedRelationId()!);
                }
            },
            { allowSignalWrites: true },
        );
    }

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

    protected selectHeadCourseCompetency(headId: string) {
        this.headCompetencyId.set(Number(headId));
        this.tailCompetencyId.set(undefined);
        this.onRelationDeselection.emit();
    }

    protected resetForm() {
        this.headCompetencyId.set(undefined);
        this.tailCompetencyId.set(undefined);
        this.relationType.set(undefined);
        this.onRelationDeselection.emit();
    }

    protected async createRelation(): Promise<void> {
        try {
            this.isLoading.set(true);
            const courseCompetencyRelation = await this.courseCompetencyApiService.createCourseCompetencyRelation(this.courseId(), {
                headCompetencyId: this.headCompetencyId()!,
                tailCompetencyId: Number(this.tailCompetencyId()!),
                relationType: this.relationType()!,
            });
            this.onRelationCreation.emit(courseCompetencyRelation);
            this.resetForm();
        } catch (error) {
            this.alertService.error(error.message);
        } finally {
            this.isLoading.set(false);
        }
    }

    protected async updateRelation(): Promise<void> {
        const currentRelation = this.relations().find(
            ({ headCompetencyId, tailCompetencyId }) => headCompetencyId == this.headCompetencyId() && tailCompetencyId == this.tailCompetencyId(),
        );
        try {
            this.isLoading.set(true);
            const newRelationType = this.relationType()!;
            await this.courseCompetencyApiService.updateCourseCompetencyRelation(this.courseId(), currentRelation!.id!, newRelationType);
            this.onRelationUpdate.emit({ ...currentRelation, relationType: newRelationType });
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
            await this.courseCompetencyApiService.deleteCourseCompetencyRelation(this.courseId(), deletedRelation!.id!);
            this.onRelationDeletion.emit(deletedRelation!.id!);
            this.resetForm();
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
                // check if direct relation already exists
                if (this.relationAlreadyExists()) {
                    return true;
                }
                // check if indirect relation already exists
                return !this.currentAdjacencyMap().get(headCompetencyId)?.includes(id!);
            })
            .filter(({ id }) => {
                const potentialRelation: CompetencyRelationDTO = {
                    headCompetencyId: headCompetencyId,
                    tailCompetencyId: id,
                    relationType: relationType,
                };
                // check if cycle would be created in potential adjacency map
                const potentialAdjacencyMap = this.getAdjacencyMap(this.relations().concat(potentialRelation));
                return !this.hasCycle(potentialAdjacencyMap);
            });
    }

    /**
     * Function to create an adjacency map from the given relations
     * @param relations The relations to create the adjacency map from
     * @private
     *
     * @returns The adjacency map
     */
    private getAdjacencyMap(relations: CompetencyRelationDTO[]): Map<number, number[]> {
        const adjacencyMap: Map<number, number[]> = new Map();
        const matchesRelations: CompetencyRelationDTO[] = [];
        relations.forEach((relation) => {
            if (!adjacencyMap.has(relation.headCompetencyId!)) {
                adjacencyMap.set(relation.headCompetencyId!, []);
            }
            if (relation.relationType == CompetencyRelationType.MATCHES) {
                // store matches relations to merge them later
                matchesRelations.push(relation);
            } else {
                // '+' used in push() is necessary to require the value to be a number
                adjacencyMap.get(relation.headCompetencyId!)!.push(+relation.tailCompetencyId!);
            }
        });
        // merge matches relations into the adjacency map
        matchesRelations.forEach((relation) => adjacencyMap.set(relation.headCompetencyId!, this.getMatchesRelationNeighbours(adjacencyMap, relation)));
        return adjacencyMap;
    }

    private getMatchesRelationNeighbours(adjacencyMap: Map<number, number[]>, relation: CompetencyRelationDTO, matchRelationIds: number[] = []): number[] {
        const existingNeighbours = adjacencyMap.get(relation.headCompetencyId!)!;
        if (relation.relationType == CompetencyRelationType.MATCHES) {
            // if current relation is a MATCHES relation, add the head competency id for next recursion steps
            matchRelationIds.push(relation.headCompetencyId!);
            matchRelationIds.push(relation.tailCompetencyId!);
            // find all relations that have the current tail competency as head competency
            const tailRelations = this.relations().filter(({ headCompetencyId }) => headCompetencyId == relation.tailCompetencyId);
            tailRelations.forEach((tailRelation) => {
                // push all neighbours of the next recursion steps to the existing neighbours of the current relation
                existingNeighbours.push(...this.getMatchesRelationNeighbours(adjacencyMap, tailRelation, matchRelationIds));
            });
        }
        const headRelations = this.relations().filter(
            ({ tailCompetencyId, relationType }) => tailCompetencyId == relation.headCompetencyId && relationType != CompetencyRelationType.MATCHES,
        );
        // add the MATCHES relations of the previous recursion steps as neighbours of the found head relations
        headRelations.forEach((headRelation) => adjacencyMap.get(headRelation.headCompetencyId!)!.push(...matchRelationIds));
        // return the existing neighbours to be added to the neighbours of previous recursion steps
        return existingNeighbours;
    }

    /**
     * Function to check if the given adjacency map has a cycle
     * @param adjacencyMap The adjacency map to check for cycles
     * @private
     *
     * @returns True if the adjacency map has a cycle, false otherwise
     */
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

    /**
     * Utility function to check if the given node has a cycle
     * @param adjacencyMap The adjacency map to check for cycles
     * @param node The node to check for cycles
     * @param visited The set of visited nodes
     * @param recursionStack The set of nodes in the recursion stack
     * @private
     *
     * @returns True if the node has a cycle, false otherwise
     */
    private hasCycleUtil(adjacencyMap: Map<number, number[]>, node: number, visited: Set<number>, recursionStack: Set<number>): boolean {
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
