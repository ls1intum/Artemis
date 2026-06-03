import { Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Edge, NgxGraphModule, NgxGraphZoomOptions, Node } from '@swimlane/ngx-graph';
import { SelectModule } from 'primeng/select';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { Subject } from 'rxjs';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import {
    CourseExerciseGroup,
    ExerciseRelation,
    ExerciseRelationEndpoint,
    ExerciseRelationEndpointKind,
    ExerciseRelationType,
} from 'app/core/course/manage/exercises/mock/course-exercise-group.model';

const NODE_WIDTH = 180;
const NODE_HEIGHT = 40;

function endpointId(endpoint: ExerciseRelationEndpoint): string {
    return endpoint.kind === ExerciseRelationEndpointKind.GROUP ? `grp-${endpoint.id}` : `ex-${endpoint.id}`;
}

function parseEndpoint(value: string): ExerciseRelationEndpoint {
    const isGroup = value.startsWith('grp-');
    return { kind: isGroup ? ExerciseRelationEndpointKind.GROUP : ExerciseRelationEndpointKind.EXERCISE, id: Number(value.replace(/^(grp|ex)-/, '')) };
}

@Component({
    selector: 'jhi-exercise-relation-graph-basic',
    templateUrl: './exercise-relation-graph-basic.component.html',
    styleUrl: './exercise-relation-graph-basic.component.scss',
    imports: [FormsModule, NgxGraphModule, SelectModule, FaIconComponent],
})
export class ExerciseRelationGraphBasicComponent {
    readonly exercises = input.required<Exercise[]>();
    readonly groups = input.required<CourseExerciseGroup[]>();
    readonly relations = input.required<ExerciseRelation[]>();

    readonly relationAdded = output<ExerciseRelation>();
    readonly relationRemoved = output<number>();

    readonly update$ = new Subject<boolean>();
    readonly center$ = new Subject<boolean>();
    readonly zoomToFit$ = new Subject<NgxGraphZoomOptions>();

    protected readonly ExerciseRelationType = ExerciseRelationType;
    protected readonly faTrash = faTrash;

    // Edit-form state.
    readonly newSource = signal<string | undefined>(undefined);
    readonly newTarget = signal<string | undefined>(undefined);
    readonly newType = signal<ExerciseRelationType>(ExerciseRelationType.PREREQUISITE);

    readonly relationTypeOptions = [
        { label: 'Prerequisite', value: ExerciseRelationType.PREREQUISITE },
        { label: 'Harder than', value: ExerciseRelationType.HARDER_THAN },
    ];

    /** All exercises and groups as selectable relation endpoints, grouped by kind. */
    readonly endpointOptions = computed(() => [
        { label: 'Groups', items: this.groups().map((group) => ({ label: group.title ?? `Group ${group.id}`, value: `grp-${group.id}` })) },
        { label: 'Exercises', items: this.exercises().map((exercise) => ({ label: exercise.title ?? `Exercise ${exercise.id}`, value: `ex-${exercise.id}` })) },
    ]);

    private readonly labelById = computed(() => {
        const map = new Map<string, string>();
        this.exercises().forEach((exercise) => map.set(`ex-${exercise.id}`, exercise.title ?? `Exercise ${exercise.id}`));
        this.groups().forEach((group) => map.set(`grp-${group.id}`, group.title ?? `Group ${group.id}`));
        return map;
    });

    readonly edges = computed<Edge[]>(() =>
        this.relations()
            .filter((relation) => relation.source && relation.target)
            .map((relation) => ({
                id: `edge-${relation.id}`,
                source: endpointId(relation.source!),
                target: endpointId(relation.target!),
                label: relation.type,
                data: { type: relation.type },
            })),
    );

    // Only render nodes that participate in a relation, so the graph stays focused on the dependency structure.
    readonly nodes = computed<Node[]>(() => {
        const referenced = new Set<string>();
        for (const edge of this.edges()) {
            referenced.add(edge.source);
            referenced.add(edge.target);
        }

        return [...referenced].map((id) => ({
            id,
            label: this.labelById().get(id) ?? id,
            dimension: { width: NODE_WIDTH, height: NODE_HEIGHT },
            data: { kind: id.startsWith('grp-') ? 'group' : 'exercise' },
        }));
    });

    /** Lists existing relations with resolved endpoint labels for the editor side panel. */
    readonly relationList = computed(() =>
        this.relations()
            .filter((relation) => relation.source && relation.target)
            .map((relation) => ({
                id: relation.id!,
                type: relation.type!,
                sourceLabel: this.labelById().get(endpointId(relation.source!)) ?? endpointId(relation.source!),
                targetLabel: this.labelById().get(endpointId(relation.target!)) ?? endpointId(relation.target!),
            })),
    );

    readonly canAdd = computed(() => {
        const source = this.newSource();
        const target = this.newTarget();
        if (!source || !target || source === target) {
            return false;
        }
        return !this.relations().some(
            (relation) =>
                relation.source && relation.target && endpointId(relation.source) === source && endpointId(relation.target) === target && relation.type === this.newType(),
        );
    });

    addRelation(): void {
        if (!this.canAdd()) {
            return;
        }
        const nextId = Math.max(0, ...this.relations().map((relation) => relation.id ?? 0)) + 1;
        this.relationAdded.emit({
            id: nextId,
            type: this.newType(),
            source: parseEndpoint(this.newSource()!),
            target: parseEndpoint(this.newTarget()!),
        });
        this.newSource.set(undefined);
        this.newTarget.set(undefined);
    }

    removeRelation(id: number): void {
        this.relationRemoved.emit(id);
    }
}
