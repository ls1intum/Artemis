import { competencyForView, knowledgeAreaForView, sourceForView } from './standardized-competency-api.mapper';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { convertToKnowledgeAreaForTree, sourceToString } from 'app/atlas/shared/entities/standardized-competency.model';
import { StandardizedCompetencyResult } from 'app/openapi/model/standardized-competency-result';
import { KnowledgeAreaResult } from 'app/openapi/model/knowledge-area-result';

describe('Standardized competency read view adapters', () => {
    it.each([
        ['REMEMBER', CompetencyTaxonomy.REMEMBER],
        ['UNDERSTAND', CompetencyTaxonomy.UNDERSTAND],
        ['APPLY', CompetencyTaxonomy.APPLY],
        ['ANALYZE', CompetencyTaxonomy.ANALYZE],
        ['EVALUATE', CompetencyTaxonomy.EVALUATE],
        ['CREATE', CompetencyTaxonomy.CREATE],
    ] satisfies [NonNullable<StandardizedCompetencyResult['taxonomy']>, CompetencyTaxonomy][])('maps taxonomy %s', (taxonomy, expected) => {
        expect(competencyForView({ id: 1, taxonomy }).taxonomy).toBe(expected);
    });

    it('preserves NON_EMPTY omissions at the UI boundary', () => {
        const absent = knowledgeAreaForView({ id: 1 });
        expect(absent.parentId).toBeUndefined();
        expect(absent.children).toBeUndefined();
        expect(absent.competencies).toBeUndefined();
        const competency = competencyForView({
            id: 2,
            title: undefined,
            description: undefined,
            taxonomy: undefined,
            sourceId: undefined,
            knowledgeAreaId: undefined,
            version: undefined,
        });
        expect(competency).toEqual({ id: 2, title: undefined, description: undefined, taxonomy: undefined, sourceId: undefined, knowledgeAreaId: undefined, version: undefined });
        const optional = knowledgeAreaForView({
            id: 3,
            parentId: undefined,
            title: undefined,
            shortTitle: undefined,
            description: undefined,
            children: undefined,
            competencies: undefined,
        });
        expect(optional).toEqual({ id: 3, parentId: undefined, title: undefined, shortTitle: undefined, description: undefined, children: undefined, competencies: undefined });
    });

    it('keeps recursive IDs and relationships and detaches UI mutations', () => {
        const input: KnowledgeAreaResult = {
            id: 1,
            title: 'Root',
            children: [{ id: 2, parentId: 1, competencies: [{ id: 3, title: 'Competency', knowledgeAreaId: 2, sourceId: 4 }] }],
        };
        const view = convertToKnowledgeAreaForTree(knowledgeAreaForView(input));
        expect(view.children?.[0]).toMatchObject({ id: 2, parentId: 1, level: 1, isVisible: true });
        expect(view.children?.[0].competencies?.[0]).toMatchObject({ id: 3, knowledgeAreaId: 2, sourceId: 4, isVisible: true });
        view.children![0].competencies![0].title = 'Edited';
        view.children![0].isVisible = false;
        expect(input.children?.[0].competencies?.[0].title).toBe('Competency');
        expect(input.children?.[0]).not.toHaveProperty('isVisible');
        expect([].map(knowledgeAreaForView)).toEqual([]);
    });

    it('maps citations without entity relationships or fake text', () => {
        expect(sourceToString(sourceForView({ id: 4, title: 'Book', author: 'Author', uri: 'https://example.org' }))).toBe('Author. "Book". https://example.org');
        const source = sourceForView({ id: 5, title: undefined, author: undefined, uri: undefined });
        expect(source).toEqual({ id: 5, title: undefined, author: undefined, uri: undefined });
        expect(source).not.toHaveProperty('competencies');
        expect(sourceToString(source)).toBe('"". ');
    });
});
