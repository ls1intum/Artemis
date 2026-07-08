import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Component } from '@angular/core';
import { KnowledgeAreaDTO, KnowledgeAreaForTree, StandardizedCompetencyDTO, convertToKnowledgeAreaForTree } from 'app/atlas/shared/entities/standardized-competency.model';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { StandardizedCompetencyFilterPageComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-filter-page.component';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

@Component({ template: '' })
class DummyImportComponent extends StandardizedCompetencyFilterPageComponent {
    protected override get knowledgeAreaTreeComponent() {
        return undefined;
    }
}

describe('StandardizedCompetencyFilterPageComponent', () => {
    setupTestBed({ zoneless: true });
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
