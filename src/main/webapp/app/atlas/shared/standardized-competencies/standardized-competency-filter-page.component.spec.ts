import { afterEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Component } from '@angular/core';
import { KnowledgeAreaDTO, KnowledgeAreaForTree, StandardizedCompetencyDTO, convertToKnowledgeAreaForTree } from 'app/atlas/shared/entities/standardized-competency.model';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { StandardizedCompetencyFilterPageComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-filter-page.component';
import {
    KnowledgeAreaTreeNode,
    TREE_NODE_TYPE_COMPETENCY,
    TREE_NODE_TYPE_KNOWLEDGE_AREA,
    convertToTreeNodes,
} from 'app/atlas/shared/standardized-competencies/knowledge-area-tree.component';

@Component({ template: '' })
class DummyImportComponent extends StandardizedCompetencyFilterPageComponent {}

describe('StandardizedCompetencyFilterPageComponent', () => {
    let componentFixture: ComponentFixture<DummyImportComponent>;
    let component: DummyImportComponent;
    let filterTree: KnowledgeAreaForTree[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(DummyImportComponent);
                component = componentFixture.componentInstance;
                const dtoTree = [
                    {
                        id: 1,
                        children: [
                            {
                                id: 11,
                                title: 'knowledge area to filter by',
                                parentId: 1,
                                children: [
                                    {
                                        id: 111,
                                        parentId: 11,
                                    },
                                ],
                            },
                            {
                                id: 12,
                                parentId: 1,
                            },
                        ],
                    },
                    {
                        id: 2,
                        children: [
                            {
                                id: 21,
                                parentId: 2,
                            },
                        ],
                    },
                ];
                filterTree = dtoTree.map((knowledgeArea) => convertToKnowledgeAreaForTree(knowledgeArea));
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should filter by knowledge area', () => {
        component['dataSource'].data = filterTree;
        filterTree.forEach((knowledgeArea) => component['addSelfAndDescendantsToMap'](knowledgeArea));
        filterTree.forEach((knowledgeArea) => component['addSelfAndDescendantsToSelectArray'](knowledgeArea));
        componentFixture.detectChanges();

        component['filterByKnowledgeArea']({ id: 11 });

        const validIds = [1, 11, 111];
        for (const knowledgeArea of component['knowledgeAreaMap'].values()) {
            if (validIds.includes(knowledgeArea.id!)) {
                expect(knowledgeArea.isVisible).toBeTruthy();
            } else {
                expect(knowledgeArea.isVisible).toBeFalsy();
            }
        }

        //test that the filter resets again
        component['filterByKnowledgeArea'](undefined);

        for (const knowledgeArea of component['knowledgeAreaMap'].values()) {
            expect(knowledgeArea.isVisible).toBeTruthy();
        }
    });

    it('should filter by title', () => {
        const filter = '   FiLter  ';
        const validIds = [1, 2, 3, 4];
        //filter matches
        const c1 = createCompetencyDTO(1, 'Filter Match1');
        const c2 = createCompetencyDTO(2, 'fIlTeR match2');
        const c3 = createCompetencyDTO(3, 'a long text filter match');
        const c4 = createCompetencyDTO(4, 'filter');
        //no filter matches
        const c11 = createCompetencyDTO(11, 'filte no match');
        const c12 = createCompetencyDTO(12, 'filteXr no match');
        const c13 = createCompetencyDTO(13, 'filte');
        const c14 = createCompetencyDTO(14, '');
        const c15 = createCompetencyDTO(15, undefined);
        const dtoTree: KnowledgeAreaDTO[] = [
            {
                id: 1,
                competencies: [c1, c2, c3, c4, c11, c12, c13, c14, c15],
                children: [{ id: 2 }],
            },
        ];
        const competencyFilterTree = dtoTree.map((knowledgeArea) => convertToKnowledgeAreaForTree(knowledgeArea));
        component['dataSource'].data = competencyFilterTree;
        competencyFilterTree.forEach((knowledgeArea) => component['addSelfAndDescendantsToMap'](knowledgeArea));
        competencyFilterTree.forEach((knowledgeArea) => component['addSelfAndDescendantsToSelectArray'](knowledgeArea));
        componentFixture.detectChanges();
        expect(component['knowledgeAreaMap'].size).toBe(2);

        component['filterByCompetencyTitle'](filter);

        const knowledgeArea = component['knowledgeAreaMap'].get(1)!;
        for (const competency of knowledgeArea.competencies!) {
            if (validIds.includes(competency.id!)) {
                expect(competency.isVisible).toBeTruthy();
            } else {
                expect(competency.isVisible).toBeFalsy();
            }
        }

        //test that the filter resets again
        component['filterByCompetencyTitle']('   ');

        for (const competency of knowledgeArea.competencies!) {
            expect(competency.isVisible).toBeTruthy();
        }
    });

    it('should initialize data structures', () => {
        filterTree.forEach((knowledgeArea) => component['addSelfAndDescendantsToMap'](knowledgeArea));
        filterTree.forEach((knowledgeArea) => component['addSelfAndDescendantsToSelectArray'](knowledgeArea));

        expect(component['knowledgeAreaMap'].size).toBe(6);
        expect(component['knowledgeAreasForSelect']).toHaveLength(6);
    });

    describe('convertToTreeNodes', () => {
        let knowledgeAreas: KnowledgeAreaForTree[];

        beforeEach(() => {
            // a top-level KA with one child KA and two competencies
            const dto: KnowledgeAreaDTO = {
                id: 1,
                title: 'root',
                competencies: [
                    { id: 100, title: 'competency 100' },
                    { id: 101, title: 'competency 101' },
                ],
                children: [
                    {
                        id: 2,
                        title: 'child',
                        parentId: 1,
                        competencies: [{ id: 200, title: 'competency 200' }],
                    },
                ],
            };
            knowledgeAreas = [convertToKnowledgeAreaForTree(dto)];
        });

        it('should convert knowledge areas and competencies to typed tree nodes', () => {
            const nodes = convertToTreeNodes(knowledgeAreas);

            expect(nodes).toHaveLength(1);
            const root = nodes[0];
            expect(root.type).toBe(TREE_NODE_TYPE_KNOWLEDGE_AREA);
            expect(root.key).toBe('knowledgeArea-1');
            // the produced node keeps the exact same domain object reference (mutations stay in sync)
            expect(root.data).toBe(knowledgeAreas[0]);

            // children must contain child knowledge areas FIRST, then competency nodes
            const children = root.children!;
            expect(children).toHaveLength(3);
            expect(children[0].type).toBe(TREE_NODE_TYPE_KNOWLEDGE_AREA);
            expect(children[0].key).toBe('knowledgeArea-2');
            expect(children[1].type).toBe(TREE_NODE_TYPE_COMPETENCY);
            expect(children[2].type).toBe(TREE_NODE_TYPE_COMPETENCY);

            // competency leaf nodes
            const competencyNode = children[1];
            expect(competencyNode.key).toBe('competency-100');
            expect(competencyNode.leaf).toBe(true);
            expect(competencyNode.data).toBe(knowledgeAreas[0].competencies![0]);
        });

        it('should omit non-visible competencies and child knowledge areas', () => {
            knowledgeAreas[0].competencies![0].isVisible = false;
            knowledgeAreas[0].children![0].isVisible = false;

            const nodes = convertToTreeNodes(knowledgeAreas);

            const children = nodes[0].children!;
            // only the visible competency (id 101) survives; the hidden competency and the hidden child KA are gone
            expect(children).toHaveLength(1);
            expect(children[0].type).toBe(TREE_NODE_TYPE_COMPETENCY);
            expect(children[0].key).toBe('competency-101');
        });

        it('should produce undefined keys for entities without an id', () => {
            const dto: KnowledgeAreaDTO = {
                title: 'no id',
                competencies: [{ title: 'competency without id' }],
            };
            const withoutId = [convertToKnowledgeAreaForTree(dto)];

            const nodes = convertToTreeNodes(withoutId);

            expect(nodes[0].key).toBeUndefined();
            expect(nodes[0].children![0].key).toBeUndefined();
        });

        it('should seed the expanded flag from the expandedKeyProvider', () => {
            const nodes = convertToTreeNodes(knowledgeAreas, (id) => id === 2);

            expect(nodes[0].expanded).toBe(false);
            expect(nodes[0].children![0].expanded).toBe(true);
        });
    });

    describe('expansion control', () => {
        let knowledgeAreas: KnowledgeAreaForTree[];

        beforeEach(() => {
            // a two-level tree: root (1) -> child (2) -> grandchild (3)
            const dto: KnowledgeAreaDTO = {
                id: 1,
                title: 'root',
                children: [
                    {
                        id: 2,
                        title: 'child',
                        parentId: 1,
                        children: [{ id: 3, title: 'grandchild', parentId: 2 }],
                    },
                ],
            };
            knowledgeAreas = [convertToKnowledgeAreaForTree(dto)];
            component['dataSource'].data = knowledgeAreas;
            knowledgeAreas.forEach((knowledgeArea) => component['addSelfAndDescendantsToMap'](knowledgeArea));
        });

        it('should expand and collapse all knowledge-area nodes', () => {
            component['tree'].expandAll();
            expectEveryKnowledgeAreaNode(component['treeNodes'](), (node) => expect(node.expanded).toBe(true));

            component['tree'].collapseAll();
            expectEveryKnowledgeAreaNode(component['treeNodes'](), (node) => expect(node.expanded).toBe(false));
        });

        it('should harvest a live (p-tree mutated) expansion so it survives a rebuild', () => {
            // simulate PrimeNG toggling the live node's expanded flag in place
            const childNode = component['treeNodes']()[0].children![0];
            expect(childNode.data!.id).toBe(2);
            childNode.expanded = true;

            // reassigning data forces dataSource.onChange -> harvestLiveExpansion -> rebuild
            component['dataSource'].data = [...knowledgeAreas];

            const rebuiltChild = component['treeNodes']()[0].children!.find((node) => node.data?.id === 2)!;
            expect(rebuiltChild.expanded).toBe(true);
            expect(component['tree'].isExpandedById(2)).toBe(true);
        });

        it('should drop a live-collapsed node id from the tracked expansion on rebuild', () => {
            // first mark node 2 as expanded in the control
            component['tree'].expand(component['knowledgeAreaMap'].get(2)!);
            expect(component['tree'].isExpandedById(2)).toBe(true);

            // simulate the user collapsing the live node directly
            const childNode = component['treeNodes']()[0].children!.find((node) => node.data?.id === 2)!;
            childNode.expanded = false;

            component['dataSource'].data = [...knowledgeAreas];

            expect(component['tree'].isExpandedById(2)).toBe(false);
            const rebuiltChild = component['treeNodes']()[0].children!.find((node) => node.data?.id === 2)!;
            expect(rebuiltChild.expanded).toBe(false);
        });
    });

    it('should expand matching knowledge areas and hide non-matching competencies on title filter', () => {
        const dto: KnowledgeAreaDTO = {
            id: 1,
            title: 'root',
            competencies: [
                { id: 100, title: 'apple' },
                { id: 101, title: 'banana' },
            ],
        };
        const tree = [convertToKnowledgeAreaForTree(dto)];
        component['dataSource'].data = tree;
        tree.forEach((knowledgeArea) => component['addSelfAndDescendantsToMap'](knowledgeArea));

        component['filterByCompetencyTitle']('apple');

        // the knowledge area containing a match is expanded in the rebuilt tree nodes
        const rootNode = component['treeNodes']()[0];
        expect(rootNode.expanded).toBe(true);
        // only the matching competency stays in the rendered children, the non-matching one is hidden
        const competencyNodes = rootNode.children!.filter((node) => node.type === TREE_NODE_TYPE_COMPETENCY);
        expect(competencyNodes).toHaveLength(1);
        expect(competencyNodes[0].key).toBe('competency-100');
    });

    it('should expand a filtered knowledge area and its ancestor', () => {
        component['dataSource'].data = filterTree;
        filterTree.forEach((knowledgeArea) => component['addSelfAndDescendantsToMap'](knowledgeArea));

        // knowledge area 11 has ancestor 1
        component['filterByKnowledgeArea']({ id: 11 });

        expect(component['tree'].isExpandedById(11)).toBe(true);
        expect(component['tree'].isExpandedById(1)).toBe(true);
    });

    /**
     * Recursively asserts on every knowledge-area node of the given tree.
     */
    function expectEveryKnowledgeAreaNode(nodes: KnowledgeAreaTreeNode[], assertion: (node: KnowledgeAreaTreeNode) => void) {
        for (const node of nodes) {
            if (node.type === TREE_NODE_TYPE_KNOWLEDGE_AREA) {
                assertion(node);
                expectEveryKnowledgeAreaNode(
                    (node.children ?? []).filter((child) => child.type === TREE_NODE_TYPE_KNOWLEDGE_AREA),
                    assertion,
                );
            }
        }
    }

    function createCompetencyDTO(id?: number, title?: string, description?: string, taxonomy?: CompetencyTaxonomy, knowledgeAreaId?: number) {
        const competency: StandardizedCompetencyDTO = {
            id: id,
            title: title,
            description: description,
            taxonomy: taxonomy,
            knowledgeAreaId: knowledgeAreaId,
        };
        return competency;
    }
});
