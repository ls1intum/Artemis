import { Competency, CompetencyTaxonomy } from 'app/entities/competency.model';

export interface StandardizedCompetency {
    id?: number;
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

export interface KnowledgeArea {
    id?: number;
    title?: string;
    description?: string;
    parent?: KnowledgeArea;
    children?: KnowledgeArea[];
    competencies?: StandardizedCompetency[];
}

export interface Source {
    title?: string;
    author?: string;
    uri?: string;
    competencies?: StandardizedCompetency[];
}

export enum StandardizedCompetencyValidators {
    TITLE_MAX = 255,
    DESCRIPTION_MAX = 2000,
}
