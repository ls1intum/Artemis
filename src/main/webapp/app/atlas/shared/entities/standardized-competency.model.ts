import { CompetencyTaxonomy, CourseCompetency } from 'app/atlas/shared/entities/competency.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { BaseCompetency } from 'app/atlas/shared/entities/competency.model';

export interface StandardizedCompetency extends BaseCompetency {
    version?: string;
    knowledgeArea?: KnowledgeArea;
    source?: Source;
    firstVersion?: StandardizedCompetency;
    childVersions?: StandardizedCompetency[];
    linkedCompetencies?: CourseCompetency[];
}

export interface StandardizedCompetencyDTO extends BaseEntity {
    title?: string;
    description?: string;
    taxonomy?: CompetencyTaxonomy;
    version?: string;
    knowledgeAreaId?: number;
    sourceId?: number;
}

export interface KnowledgeArea extends BaseEntity {
    title?: string;
    shortTitle?: string;
    description?: string;
    parent?: KnowledgeArea;
    children?: KnowledgeArea[];
    competencies?: StandardizedCompetency[];
}

export interface KnowledgeAreaDTO extends BaseEntity {
    title?: string;
    shortTitle?: string;
    description?: string;
    parentId?: number;
    children?: KnowledgeAreaDTO[];
    competencies?: StandardizedCompetencyDTO[];
}

export interface Source extends BaseEntity {
    title?: string;
    author?: string;
    uri?: string;
    competencies?: StandardizedCompetency[];
}

export enum StandardizedCompetencyValidators {
    TITLE_MAX = 255,
    DESCRIPTION_MAX = 2000,
}

export enum KnowledgeAreaValidators {
    TITLE_MAX = 255,
    SHORT_TITLE_MAX = 10,
    DESCRIPTION_MAX = 2000,
}

/**
 * KnowledgeAreaDTO with additional information for the tree view:
 * isVisible: if it should be shown or not (used for filters)
 * level: nesting level (used for indentations)
 */
export interface KnowledgeAreaForTree extends KnowledgeAreaDTO {
    isVisible: boolean;
    level: number;
    children?: KnowledgeAreaForTree[];
    competencies?: StandardizedCompetencyForTree[];
}

export interface KnowledgeAreasForImportDTO {
    knowledgeAreas: KnowledgeAreaDTO[];
    sources: Source[];
}

/**
 * StandardizedCompetencyDTO with additional information for the tree view
 * isVisible: if it should be shown or not (used for filters)
 */
export interface StandardizedCompetencyForTree extends StandardizedCompetencyDTO {
    isVisible: boolean;
}

export function sourceToString(source: Source) {
    const author = source.author ?? '';
    const title = source.title ?? '';
    const uri = source.uri ?? '';

    if (!author) {
        return `"${title}". ${uri}`;
    } else {
        return `${author}. "${title}". ${uri}`;
    }
}

export function convertToStandardizedCompetencyForTree(competencyDTO: StandardizedCompetencyDTO, isVisible: boolean) {
    const competencyForTree: StandardizedCompetencyForTree = Object.assign({}, competencyDTO, { isVisible: isVisible });
    return competencyForTree;
}

export function convertToKnowledgeAreaForTree(knowledgeAreaDTO: KnowledgeAreaDTO, isVisible = true, level = 0): KnowledgeAreaForTree {
    const children = knowledgeAreaDTO.children?.map((child) => convertToKnowledgeAreaForTree(child, isVisible, level + 1));
    const competencies = knowledgeAreaDTO.competencies?.map((competency) => convertToStandardizedCompetencyForTree(competency, isVisible));
    return Object.assign({}, knowledgeAreaDTO, { children: children, competencies: competencies, level: level, isVisible: isVisible });
}
