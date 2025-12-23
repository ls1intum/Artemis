import { Component, computed, effect, inject, input, model, signal } from '@angular/core';
import { CompetencyRelationDTO, CompetencyRelationType, CourseCompetency, UpdateCourseCompetencyRelationDTO } from 'app/atlas/shared/entities/competency.model';

import { CourseCompetencyApiService } from 'app/atlas/shared/services/course-competency-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { faLightbulb, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';

interface SuggestedRelationDTO {
    tail_id: string;
    head_id: string;
    relation_type: string; // "MATCHES" | "EXTENDS" | "REQUIRES"
}

@Component({
    selector: 'jhi-course-competency-relation-form',
    templateUrl: './course-competency-relation-form.component.html',
    styleUrl: './course-competency-relation-form.component.scss',
    imports: [TranslateDirective, CommonModule, FontAwesomeModule, FormsModule, FeatureToggleHideDirective, ButtonComponent],
})
export class CourseCompetencyRelationFormComponent {
    protected readonly faSpinner = faSpinner;
    protected readonly faLightbulb = faLightbulb;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonType = ButtonType;

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

    readonly suggestedRelations = signal<SuggestedRelationDTO[]>([]);
    readonly isLoadingSuggestions = signal<boolean>(false);
    readonly selectedSuggestions = signal<Set<number>>(new Set());
    readonly selectedSuggestionsCount = computed(() => this.selectedSuggestions().size);
    readonly shouldShowSuggestionsButton = computed(() => this.courseCompetencies().length > 1);

    constructor() {
        effect(() => this.selectRelation(this.selectedRelationId()));
        // Suggestions are fetched on demand via user action
    }

    async fetchSuggestions(): Promise<void> {
        await this.loadSuggestedRelations(this.courseId());
    }

    private async loadSuggestedRelations(courseId: number) {
        try {
            this.isLoadingSuggestions.set(true);
            const response = await this.courseCompetencyApiService.getSuggestedCompetencyRelations(courseId);
            this.suggestedRelations.set(response.relations ?? []);
            // Auto-select all suggestions when fetched, but exclude existing relations
            const allIndices = new Set((response.relations ?? []).map((_, index) => index).filter((index) => !this.doesSuggestionAlreadyExist(response.relations![index])));
            this.selectedSuggestions.set(allIndices);
        } catch (error) {
            // Non-blocking: show toast but keep UI working
            this.alertService.warning('Failed to load suggested relations');
        } finally {
            this.isLoadingSuggestions.set(false);
        }
    }

    protected getUiRelationTypeKey(s: SuggestedRelationDTO): keyof typeof CompetencyRelationType {
        // Map server "REQUIRES" to client enum key "ASSUMES"
        const key = s.relation_type === 'REQUIRES' ? 'ASSUMES' : s.relation_type;
        // Fallback safety
        if (key in CompetencyRelationType) {
            return key as keyof typeof CompetencyRelationType;
        }
        return 'ASSUMES';
    }

    protected applySuggestion(s: SuggestedRelationDTO) {
        const headId = Number(s.head_id);
        const tailId = Number(s.tail_id);
        const uiKey = this.getUiRelationTypeKey(s);
        const type = CompetencyRelationType[uiKey];
        this.headCompetencyId.set(headId);
        this.tailCompetencyId.set(tailId);
        this.relationType.set(type);
        const existing = this.getExactRelation(headId, tailId, type);
        this.selectedRelationId.set(existing?.id);
    }

    protected toggleSuggestionSelection(index: number): void {
        // Don't allow selection of existing relations
        const suggestion = this.suggestedRelations()[index];
        if (this.doesSuggestionAlreadyExist(suggestion)) {
            return;
        }

        this.selectedSuggestions.update((selected) => {
            const newSelected = new Set(selected);
            if (newSelected.has(index)) {
                newSelected.delete(index);
            } else {
                newSelected.add(index);
            }
            return newSelected;
        });
    }

    protected isSuggestionSelected(index: number): boolean {
        return this.selectedSuggestions().has(index);
    }

    protected doesSuggestionAlreadyExist(s: SuggestedRelationDTO): boolean {
        const headId = Number(s.head_id);
        const tailId = Number(s.tail_id);
        const uiKey = this.getUiRelationTypeKey(s);
        const type = CompetencyRelationType[uiKey];
        return this.getExactRelation(headId, tailId, type) !== undefined;
    }

    protected async addSelectedSuggestions(): Promise<void> {
        const selectedIndices = Array.from(this.selectedSuggestions());
        const selectedSuggestions = selectedIndices.map((index) => this.suggestedRelations()[index]);

        if (selectedSuggestions.length === 0) {
            return;
        }

        try {
            this.isLoading.set(true);
            const createdRelations: CompetencyRelationDTO[] = [];

            for (const suggestion of selectedSuggestions) {
                const headId = Number(suggestion.head_id);
                const tailId = Number(suggestion.tail_id);
                const uiKey = this.getUiRelationTypeKey(suggestion);
                const type = CompetencyRelationType[uiKey];

                // Check if relation already exists
                const existing = this.getExactRelation(headId, tailId, type);
                if (!existing) {
                    try {
                        const courseCompetencyRelation = await this.courseCompetencyApiService.createCourseCompetencyRelation(this.courseId(), {
                            headCompetencyId: headId,
                            tailCompetencyId: tailId,
                            relationType: type,
                        });
                        createdRelations.push(courseCompetencyRelation);
                    } catch (error) {
                        // Continue with other suggestions even if one fails
                        this.alertService.error(`Failed to create relation: ${this.getCompetencyTitleById(headId)} → ${this.getCompetencyTitleById(tailId)}`);
                    }
                }
            }

            // Update relations with all successfully created relations
            this.relations.update((relations) => [...relations, ...createdRelations]);

            // Clear selections and suggestions
            this.selectedSuggestions.set(new Set());
            this.suggestedRelations.set([]);

            if (createdRelations.length > 0) {
                this.alertService.success(`Successfully added ${createdRelations.length} relation(s)`);
            }
        } catch (error) {
            this.alertService.error('Failed to add selected suggestions');
        } finally {
            this.isLoading.set(false);
        }
    }

    protected getCompetencyTitleById(idLike: number | string): string {
        const id = Number(idLike);
        const found = this.courseCompetencies().find((c) => c.id === id);
        return found?.title ?? String(idLike);
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
        if (!this.headCompetencyId()) {
            this.selectHeadCourseCompetency(courseCompetencyId);
        } else if (!this.tailCompetencyId()) {
            this.selectTailCourseCompetency(courseCompetencyId);
        } else {
            this.selectHeadCourseCompetency(courseCompetencyId);
        }
    }

    protected selectHeadCourseCompetency(headId: number) {
        this.headCompetencyId.set(headId);
        this.tailCompetencyId.set(undefined);
        this.selectedRelationId.set(undefined);
    }

    protected selectTailCourseCompetency(tailId: number) {
        this.tailCompetencyId.set(tailId);
        const existingRelation = this.getRelation(this.headCompetencyId(), this.tailCompetencyId());
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
            if (!deletedRelation) {
                this.isLoading.set(false);
                return;
            }
            await this.courseCompetencyApiService.deleteCourseCompetencyRelation(this.courseId(), deletedRelation.id!);
            this.relations.update((relations) => relations.filter(({ id }) => id !== deletedRelation.id));
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
     */
    private getSelectableTailCompetencyIds(headCompetencyId: number, relationType: CompetencyRelationType): number[] {
        return this.courseCompetencies()
            .map(({ id }) => id!)
            .filter((id) => id !== headCompetencyId)
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

    private detectCycleInRelations(relations: CompetencyRelationDTO[], numOfCompetencies: number): boolean {
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
        const unionFind = new UnionFind(numOfCompetencies);
        relations.forEach((relation) => {
            if (relation.relationType === CompetencyRelationType.MATCHES) {
                const tailIndex = idToIndexMap.get(relation.tailCompetencyId!);
                const headIndex = idToIndexMap.get(relation.headCompetencyId!);
                if (tailIndex !== undefined && headIndex !== undefined) {
                    unionFind.union(tailIndex, headIndex);
                }
            }
        });
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

export class UnionFind {
    parent: number[];
    rank: number[];
    constructor(size: number) {
        this.parent = Array.from({ length: size }, (_, index) => index);
        this.rank = Array(size).fill(1);
    }
    public find(competencyId: number): number {
        if (this.parent[competencyId] !== competencyId) {
            this.parent[competencyId] = this.find(this.parent[competencyId]);
        }
        return this.parent[competencyId];
    }
    public union(tailCompetencyId: number, headCompetencyId: number) {
        const rootU = this.find(tailCompetencyId);
        const rootV = this.find(headCompetencyId);
        if (rootU !== rootV) {
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
