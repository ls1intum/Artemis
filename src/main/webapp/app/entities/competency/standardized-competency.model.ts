import { Competency, CompetencyTaxonomy } from 'app/entities/competency.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export interface StandardizedCompetency extends BaseEntity {
    title?: string;
    description?: string;
    taxonomy?: CompetencyTaxonomy;
    version?: string;
    knowledgeArea?: KnowledgeArea;
    source?: Source;
    firstVersion?: StandardizedCompetency;
    childVersions?: StandardizedCompetency[];
    linkedCompetencies?: Competency[];
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

/**
 * StandardizedCompetencyDTO with additional information for the tree view
 * isVisible: if it should be shown or not (used for filters)
 */
export interface StandardizedCompetencyForTree extends StandardizedCompetencyDTO {
    isVisible: boolean;
}

export function convertToStandardizedCompetencyDTO(competency: StandardizedCompetency) {
    const competencyDTO: StandardizedCompetencyDTO = {
        id: competency.id,
        title: competency.title,
        description: competency.description,
        taxonomy: competency.taxonomy,
        version: competency.version,
        knowledgeAreaId: competency.knowledgeArea?.id,
        sourceId: competency.source?.id,
    };
    return competencyDTO;
}

export function convertToStandardizedCompetency(competencyDTO: StandardizedCompetencyDTO) {
    const competency: StandardizedCompetency = {
        id: competencyDTO.id,
        title: competencyDTO.title,
        description: competencyDTO.description,
        taxonomy: competencyDTO.taxonomy,
        version: competencyDTO.version,
    };

    if (competencyDTO.knowledgeAreaId) {
        competency.knowledgeArea = {
            id: competencyDTO.knowledgeAreaId,
        };
    }
    if (competencyDTO.sourceId) {
        competency.source = {
            id: competencyDTO.sourceId,
        };
    }

    return competency;
}

export function convertToStandardizedCompetencyForTree(competencyDTO: StandardizedCompetencyDTO, isVisible: boolean) {
    const competencyForTree: StandardizedCompetencyForTree = { ...competencyDTO, isVisible: isVisible };
    return competencyForTree;
}
