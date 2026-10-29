import { KnowledgeAreaResult } from 'app/openapi/model/knowledge-area-result';
import { StandardizedCompetencyResult } from 'app/openapi/model/standardized-competency-result';
import { Source as ApiSource } from 'app/openapi/model/source';
import { KnowledgeAreaDTO, Source, StandardizedCompetencyDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';

const taxonomyForView = {
    REMEMBER: CompetencyTaxonomy.REMEMBER,
    UNDERSTAND: CompetencyTaxonomy.UNDERSTAND,
    APPLY: CompetencyTaxonomy.APPLY,
    ANALYZE: CompetencyTaxonomy.ANALYZE,
    EVALUATE: CompetencyTaxonomy.EVALUATE,
    CREATE: CompetencyTaxonomy.CREATE,
} satisfies Record<NonNullable<StandardizedCompetencyResult['taxonomy']>, CompetencyTaxonomy>;

/** Detach generated read data and normalize nullable properties for mutable UI/admin drafts. */
export function competencyForView(competency: StandardizedCompetencyResult): StandardizedCompetencyDTO {
    return {
        id: competency.id,
        title: competency.title ?? undefined,
        description: competency.description ?? undefined,
        taxonomy: competency.taxonomy == null ? undefined : taxonomyForView[competency.taxonomy],
        version: competency.version ?? undefined,
        knowledgeAreaId: competency.knowledgeAreaId ?? undefined,
        sourceId: competency.sourceId ?? undefined,
    };
}

/** Preserve absent collections while detaching a recursive catalog response. */
export function knowledgeAreaForView(area: KnowledgeAreaResult): KnowledgeAreaDTO {
    return {
        id: area.id,
        title: area.title ?? undefined,
        shortTitle: area.shortTitle ?? undefined,
        description: area.description ?? undefined,
        parentId: area.parentId ?? undefined,
        children: area.children?.map(knowledgeAreaForView),
        competencies: area.competencies?.map(competencyForView),
    };
}

/** Only citation data crosses from the read contract into the existing admin/UI source shape. */
export function sourceForView(source: ApiSource): Source {
    return { id: source.id, title: source.title ?? undefined, author: source.author ?? undefined, uri: source.uri ?? undefined };
}
