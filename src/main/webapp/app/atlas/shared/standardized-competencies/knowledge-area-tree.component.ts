import { Component, TemplateRef, contentChild, input } from '@angular/core';
import { KnowledgeAreaForTree, StandardizedCompetencyForTree } from 'app/atlas/shared/entities/standardized-competency.model';
import { TreeModule } from 'primeng/tree';
import { TreeNode } from 'primeng/api';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CommonModule } from '@angular/common';

/**
 * The value stored on {@link TreeNode.type}. PrimeNG `<p-tree>` resolves the node template by this type, so the template
 * declares one `pTemplate` per type (`pTemplate="knowledgeArea"` / `pTemplate="competency"`). This mirrors the previous dual
 * content-template projection (knowledge-area rows vs. competency rows).
 */
export const TREE_NODE_TYPE_KNOWLEDGE_AREA = 'knowledgeArea';
export const TREE_NODE_TYPE_COMPETENCY = 'competency';

/**
 * A node in the knowledge-area tree. A knowledge-area node and a competency node share the same TreeNode type because a
 * knowledge area's `children` are heterogeneous (child knowledge areas followed by competency leaves), which a per-type
 * generic cannot express. The concrete domain object is kept on {@link TreeNode.data} and disambiguated at runtime via
 * {@link TreeNode.type}; a competency's knowledge area is reachable via `parent.data`.
 */
export type KnowledgeAreaTreeNode = TreeNode<KnowledgeAreaForTree | StandardizedCompetencyForTree>;
/** Alias kept for readability at competency call sites; structurally identical to {@link KnowledgeAreaTreeNode}. */
export type CompetencyTreeNode = KnowledgeAreaTreeNode;

/**
 * Converts the knowledge-area / competency domain hierarchy into a PrimeNG {@link TreeNode} array consumed by `<p-tree>`.
 *
 * Each knowledge area becomes a {@link TreeNode} of type {@link TREE_NODE_TYPE_KNOWLEDGE_AREA}. Its child knowledge areas
 * and its competencies are both modelled as child {@link TreeNode}s; competencies are leaf nodes of type
 * {@link TREE_NODE_TYPE_COMPETENCY}. The original domain object is always kept on {@link TreeNode.data} so that templates and
 * handlers can access it (and so the `isVisible` / CRUD logic that mutates the domain objects in place keeps working on the
 * same object references).
 *
 * Visibility filtering is reflected by omitting non-visible domain objects from the produced array, because PrimeNG `<p-tree>`
 * has no per-node "hidden" flag. The `isVisible` field stays the source of truth on the domain objects.
 *
 * Expansion state cannot be derived from the domain model, so it is taken from {@link expandedKeyProvider}: it is asked, by
 * knowledge-area id, whether a node should be expanded. This lets callers preserve user expansion state across rebuilds.
 *
 * @param knowledgeAreas the visible top-level knowledge areas
 * @param expandedKeyProvider returns the expansion state for a knowledge area (defaults to collapsed)
 */
export function convertToTreeNodes(
    knowledgeAreas: KnowledgeAreaForTree[] | undefined,
    expandedKeyProvider: (id: number | undefined) => boolean = () => false,
): KnowledgeAreaTreeNode[] {
    return (knowledgeAreas ?? []).filter((knowledgeArea) => knowledgeArea.isVisible).map((knowledgeArea) => convertKnowledgeAreaToTreeNode(knowledgeArea, expandedKeyProvider));
}

function convertKnowledgeAreaToTreeNode(knowledgeArea: KnowledgeAreaForTree, expandedKeyProvider: (id: number | undefined) => boolean): KnowledgeAreaTreeNode {
    const childKnowledgeAreas = convertToTreeNodes(knowledgeArea.children, expandedKeyProvider);
    const competencyNodes: CompetencyTreeNode[] = (knowledgeArea.competencies ?? [])
        .filter((competency) => competency.isVisible)
        .map((competency) => ({
            key: competency.id !== undefined ? `competency-${competency.id}` : undefined,
            label: competency.title,
            data: competency,
            type: TREE_NODE_TYPE_COMPETENCY,
            leaf: true,
        }));
    return {
        key: knowledgeArea.id !== undefined ? `knowledgeArea-${knowledgeArea.id}` : undefined,
        label: knowledgeArea.title,
        data: knowledgeArea,
        type: TREE_NODE_TYPE_KNOWLEDGE_AREA,
        expanded: expandedKeyProvider(knowledgeArea.id),
        children: [...childKnowledgeAreas, ...competencyNodes],
    };
}

@Component({
    selector: 'jhi-knowledge-area-tree',
    templateUrl: './knowledge-area-tree.component.html',
    styleUrls: ['./knowledge-area-tree.component.scss'],
    imports: [TreeModule, TranslateDirective, CommonModule],
})
export class KnowledgeAreaTreeComponent {
    /** The PrimeNG {@link TreeNode} array rendered by `<p-tree>`. */
    nodes = input<KnowledgeAreaTreeNode[]>([]);

    readonly knowledgeAreaTemplate = contentChild<TemplateRef<{ knowledgeArea: KnowledgeAreaForTree }>>('knowledgeAreaTemplate');
    readonly competencyTemplate = contentChild<TemplateRef<{ competency: StandardizedCompetencyForTree; knowledgeArea: KnowledgeAreaForTree }>>('competencyTemplate');

    /** Exposed for the template so the single `<p-tree>` node template can branch on the node's `type`. */
    protected readonly TREE_NODE_TYPE_KNOWLEDGE_AREA = TREE_NODE_TYPE_KNOWLEDGE_AREA;
    protected readonly TREE_NODE_TYPE_COMPETENCY = TREE_NODE_TYPE_COMPETENCY;
}
