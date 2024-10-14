import { Component, computed, effect, inject, input, model, signal } from '@angular/core';
import { CompetencyRelationDTO, CompetencyRelationType, CourseCompetency, UpdateCourseCompetencyRelationDTO } from 'app/entities/competency.model';
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
    readonly relations = model.required<CompetencyRelationDTO[]>();
    readonly selectedRelationId = model.required<number | undefined>();

    readonly headCompetencyId = signal<number | undefined>(undefined);
    readonly tailCompetencyId = signal<number | undefined>(undefined);
    readonly relationType = model<CompetencyRelationType | undefined>(undefined);

    readonly isLoading = signal<boolean>(false);

    readonly relationAlreadyExists = computed(() => this.getRelation(this.headCompetencyId(), this.tailCompetencyId()) !== undefined);
    readonly exactRelationAlreadyExists = computed(() => this.getExactRelation(this.headCompetencyId(), this.tailCompetencyId(), this.relationType()) !== undefined);

    private readonly selectableTailCourseCompetencyIds = computed(() => {
        if (this.headCompetencyId() && this.relationType()) {
            return this.getSelectableTailCompetencyIds(this.headCompetencyId()!, this.relationType()!);
        }
        return this.courseCompetencies().map(({ id }) => id!);
    });

    readonly showCircularDependencyError = computed(() => this.tailCompetencyId() && !this.selectableTailCourseCompetencyIds().includes(this.tailCompetencyId()!));

    constructor() {
        effect(() => this.selectRelation(this.selectedRelationId()), { allowSignalWrites: true });
    }

    protected isCourseCompetencySelectable(courseCompetencyId: number): boolean {
        return this.selectableTailCourseCompetencyIds().includes(courseCompetencyId);
    }

    private selectRelation(relationId?: number): void {
        const relation = this.relations().find(({ id }) => id === relationId);
        if (relation) {
            this.headCompetencyId.set(relation?.headCompetencyId);
            this.tailCompetencyId.set(relation?.tailCompetencyId);
            this.relationType.set(relation?.relationType);
        }
    }

    public selectCourseCompetency(courseCompetencyId: number): void {
        if (!this.headCompetencyId() || this.tailCompetencyId()) {
            this.headCompetencyId.set(courseCompetencyId);
            this.tailCompetencyId.set(undefined);
            this.relationType.set(undefined);
        } else {
            if (this.selectableTailCourseCompetencyIds().find((id) => id === courseCompetencyId)) {
                this.tailCompetencyId.set(courseCompetencyId);
            }
        }
    }

    protected selectHeadCourseCompetency(headId: string) {
        this.headCompetencyId.set(Number(headId));
        this.tailCompetencyId.set(undefined);
        this.selectedRelationId.set(undefined);
    }

    protected selectTailCourseCompetency(tailId: string) {
        this.tailCompetencyId.set(Number(tailId));
        const existingRelation = this.getExactRelation(this.headCompetencyId(), this.tailCompetencyId(), this.relationType());
        if (existingRelation) {
            this.selectedRelationId.set(existingRelation.id);
        } else {
            this.selectedRelationId.set(undefined);
        }
    }

    protected async createRelation(): Promise<void> {
        try {
            this.isLoading.set(true);
            const courseCompetencyRelation = await this.courseCompetencyApiService.createCourseCompetencyRelation(this.courseId(), {
                headCompetencyId: this.headCompetencyId()!,
                tailCompetencyId: Number(this.tailCompetencyId()!),
                relationType: this.relationType()!,
            });
            this.relations.update((relations) => [...relations, courseCompetencyRelation]);
            this.selectedRelationId.set(courseCompetencyRelation.id!);
        } catch (error) {
            this.alertService.error(error.message);
        } finally {
            this.isLoading.set(false);
        }
    }

    protected getExactRelation(headCompetencyId?: number, tailCompetencyId?: number, relationType?: CompetencyRelationType): CompetencyRelationDTO | undefined {
        return this.relations().find(
            (relation) => relation.headCompetencyId === headCompetencyId && relation.tailCompetencyId === tailCompetencyId && relation.relationType === relationType,
        );
    }

    protected getRelation(headCompetencyId?: number, tailCompetencyId?: number): CompetencyRelationDTO | undefined {
        return this.relations().find((relation) => relation.headCompetencyId === headCompetencyId && relation.tailCompetencyId === tailCompetencyId);
    }

    protected async updateRelation(): Promise<void> {
        try {
            this.isLoading.set(true);
            const newRelationType = this.relationType()!;
            await this.courseCompetencyApiService.updateCourseCompetencyRelation(this.courseId(), this.selectedRelationId()!, <UpdateCourseCompetencyRelationDTO>{
                newRelationType: newRelationType,
            });
            this.relations.update((relations) =>
                relations.map((relation) => {
                    if (relation.id === this.selectedRelationId()) {
                        return { ...relation, relationType: newRelationType };
                    }
                    return relation;
                }),
            );
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
            this.relations.update((relations) => relations.filter(({ id }) => id !== deletedRelation!.id));
            this.selectedRelationId.set(undefined);
        } catch (error) {
            this.alertService.error(error.message);
        } finally {
            this.isLoading.set(false);
        }
    }

    /**
     * Function to get the selectable tail competency ids for the given head
     * competency and relation type without creating a cyclic dependency
     *
     * @param headCompetencyId The selected head competency id
     * @param relationType The selected relation type
     * @private
     *
     * @returns The selectable tail competency ids
     */
    private getSelectableTailCompetencyIds(headCompetencyId: number, relationType: CompetencyRelationType): number[] {
        return this.courseCompetencies()
            .map(({ id }) => id!)
            .filter((id) => id !== headCompetencyId) // Exclude the head itself
            .filter((id) => {
                let relations = this.relations();
                const existingRelation = this.getRelation(headCompetencyId, id);
                if (existingRelation) {
                    relations = relations.filter((relation) => relation.id !== existingRelation.id);
                }
                const potentialRelation: CompetencyRelationDTO = {
                    headCompetencyId: headCompetencyId,
                    tailCompetencyId: id,
                    relationType: relationType,
                };
                return !this.detectCycleInRelations(relations.concat(potentialRelation), this.courseCompetencies().length);
            });
    }

    /**
     * Function to detect cycles in the competency relations
     * @param relations The list of competency relations
     * @param numOfCompetencies The total number of competencies
     * @private
     *
     * @returns True if a cycle is detected, false otherwise
     */
    private detectCycleInRelations(relations: CompetencyRelationDTO[], numOfCompetencies: number): boolean {
        // Create a map to store the competency IDs and map them to incremental indices
        const idToIndexMap = new Map<number, number>();
        let currentIndex = 0;

        // map the competency IDs to incremental indices
        relations.forEach((relation) => {
            const tail = relation.tailCompetencyId!;
            const head = relation.headCompetencyId!;

            if (!idToIndexMap.has(tail)) {
                idToIndexMap.set(tail, currentIndex++);
            }
            if (!idToIndexMap.has(head)) {
                idToIndexMap.set(head, currentIndex++);
            }
        });

        const unionFind = new UnionFind(numOfCompetencies);

        // Apply Union-Find based on the MATCHES relations
        relations.forEach((relation) => {
            if (relation.relationType === CompetencyRelationType.MATCHES) {
                const tailIndex = idToIndexMap.get(relation.tailCompetencyId!);
                const headIndex = idToIndexMap.get(relation.headCompetencyId!);

                if (tailIndex !== undefined && headIndex !== undefined) {
                    // Perform union operation to group matching course competencies into sets
                    unionFind.union(tailIndex, headIndex);
                }
            }
        });

        // Build the reduced graph for EXTENDS and ASSUMES relations
        const reducedGraph: number[][] = Array.from({ length: numOfCompetencies }, () => []);

        relations.forEach((relation) => {
            const tail = unionFind.find(idToIndexMap.get(relation.tailCompetencyId!)!);
            const head = unionFind.find(idToIndexMap.get(relation.headCompetencyId!)!);

            if (relation.relationType === CompetencyRelationType.EXTENDS || relation.relationType === CompetencyRelationType.ASSUMES) {
                reducedGraph[tail].push(head);
            }
        });

        return this.hasCycle(reducedGraph, numOfCompetencies);
    }

    private hasCycle(graph: number[][], noOfCourseCompetencies: number): boolean {
        const visited: boolean[] = Array(noOfCourseCompetencies).fill(false);
        const recursionStack: boolean[] = Array(noOfCourseCompetencies).fill(false);

        // Depth-first search to detect cycles
        const depthFirstSearch = (v: number): boolean => {
            visited[v] = true;
            recursionStack[v] = true;

            for (const neighbor of graph[v] || []) {
                if (!visited[neighbor]) {
                    if (depthFirstSearch(neighbor)) return true;
                } else if (recursionStack[neighbor]) {
                    return true;
                }
            }

            recursionStack[v] = false;
            return false;
        };

        for (let node = 0; node < noOfCourseCompetencies; node++) {
            if (!visited[node]) {
                if (depthFirstSearch(node)) {
                    return true;
                }
            }
        }
        return false;
    }
}

// Union-Find (Disjoint Set) class (https://en.wikipedia.org/wiki/Disjoint-set_data_structure -> union by rank)
class UnionFind {
    parent: number[];
    rank: number[];

    constructor(size: number) {
        this.parent = Array.from({ length: size }, (_, index) => index);
        this.rank = Array(size).fill(1);
    }

    // Find the representative of the set that contains the `competencyId`
    public find(competencyId: number): number {
        if (this.parent[competencyId] !== competencyId) {
            this.parent[competencyId] = this.find(this.parent[competencyId]); // Path compression
        }
        return this.parent[competencyId];
    }

    // Union the sets containing `tailCompetencyId` and `headCompetencyId`
    public union(tailCompetencyId: number, headCompetencyId: number) {
        const rootU = this.find(tailCompetencyId);
        const rootV = this.find(headCompetencyId);
        if (rootU !== rootV) {
            // Union by rank
            if (this.rank[rootU] > this.rank[rootV]) {
                this.parent[rootV] = rootU;
            } else if (this.rank[rootU] < this.rank[rootV]) {
                this.parent[rootU] = rootV;
            } else {
                this.parent[rootV] = rootU;
                this.rank[rootU] += 1;
            }
        }
    }
}
