import { Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
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
            await this.courseCompetencyApiService.updateCourseCompetencyRelation(this.courseId(), currentRelation!.id!, <UpdateCourseCompetencyRelationDTO>{
                newRelationType: newRelationType,
            });
            this.onRelationUpdate.emit({ ...currentRelation, relationType: newRelationType });
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
                const potentialRelation: CompetencyRelationDTO = {
                    headCompetencyId: headCompetencyId,
                    tailCompetencyId: id,
                    relationType: relationType,
                };
                return !this.detectCycleInCompetencyRelations(this.relations().concat(potentialRelation), this.courseCompetencies().length);
            });
    }

    private detectCycleInCompetencyRelations(relations: CompetencyRelationDTO[], numOfCompetencies: number): boolean {
        // Step 1: Create a map to store the unique IDs and map them to incremental indices
        const idToIndexMap = new Map<number, number>();
        let currentIndex = 0;

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

        const numCompetencies = currentIndex; // Total unique competencies
        const unionFind = new UnionFind(numCompetencies);

        // Step 2: Apply Union-Find based on the MATCHES relations
        relations.forEach((relation) => {
            if (relation.relationType === CompetencyRelationType.MATCHES) {
                const tailIndex = idToIndexMap.get(relation.tailCompetencyId!);
                const headIndex = idToIndexMap.get(relation.headCompetencyId!);

                if (tailIndex !== undefined && headIndex !== undefined) {
                    // Perform union operation to group competencies
                    unionFind.union(tailIndex, headIndex);
                }
            }
        });

        // Step 2: Build the reduced graph for EXTENDS and ASSUMES relations
        const reducedGraph: number[][] = Array.from({ length: numCompetencies }, () => []);

        relations.forEach((relation) => {
            const tail = unionFind.find(idToIndexMap.get(relation.tailCompetencyId!)!);
            const head = unionFind.find(idToIndexMap.get(relation.headCompetencyId!)!);

            if (relation.relationType === CompetencyRelationType.EXTENDS || relation.relationType === CompetencyRelationType.ASSUMES) {
                reducedGraph[tail].push(head);
            }
        });

        // Step 3: Detect cycles in the reduced graph
        return this.hasCycle(reducedGraph, numOfCompetencies);
    }

    private hasCycle(graph: number[][], n: number): boolean {
        const visited: boolean[] = Array(n).fill(false);
        const recursionStack: boolean[] = Array(n).fill(false);

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

        for (let node = 0; node < n; node++) {
            if (!visited[node]) {
                if (depthFirstSearch(node)) {
                    return true;
                }
            }
        }
        return false;
    }
}

// Union-Find (Disjoint Set) class
class UnionFind {
    parent: number[];
    rank: number[];

    constructor(size: number) {
        this.parent = Array.from({ length: size }, (_, index) => index);
        this.rank = Array(size).fill(1);
    }

    // Find the representative of the set that contains `u`
    find(competencyId: number): number {
        if (this.parent[competencyId] !== competencyId) {
            this.parent[competencyId] = this.find(this.parent[competencyId]); // Path compression
        }
        return this.parent[competencyId];
    }

    // Union the sets containing `tailCompetencyId` and `headCompetencyId`
    union(tailCompetencyId: number, headCompetencyId: number) {
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
