import { CompetencyRelationType, CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import dayjs from 'dayjs/esm';

export interface ChatMessage {
    id: string;
    content: string;
    isUser: boolean;
    timestamp: Date;
    canCreateCompetencies?: boolean;
    suggestedCompetencies?: CompetencyDraft[];
    competencyPreview?: CompetencyPreview;
    batchCompetencyPreview?: CompetencyPreview[]; // For batch operations (multiple competencies)
    relationPreview?: CompetencyRelationPreview;
    batchRelationPreview?: CompetencyRelationPreview[]; // For batch relation operations
    relationGraphPreview?: RelationGraphPreview; // Graph visualization for relation preview
    competencyCreated?: boolean;
    batchCreated?: boolean; // For batch creation/update completion
    relationCreated?: boolean;
    batchRelationCreated?: boolean; // For batch relation creation completion
    planPending?: boolean;
    planApproved?: boolean;
}

export interface CompetencyDraft {
    title: string;
    description: string;
    taxonomy: CompetencyTaxonomy;
    masteryThreshold: number;
    optional: boolean;
    softDueDate?: dayjs.Dayjs;
}

export interface CompetencyPreview {
    title: string;
    description: string;
    taxonomy: CompetencyTaxonomy;
    icon?: string;
    competencyId?: number; // Optional: present when updating existing competency
    viewOnly?: boolean; // Optional: when true, no action buttons are shown
}

export interface CompetencyRelationPreview {
    relationId?: number; // Optional: present when updating existing relation
    headCompetencyId: number;
    headCompetencyTitle: string;
    tailCompetencyId: number;
    tailCompetencyTitle: string;
    relationType: CompetencyRelationType;
    viewOnly?: boolean; // Optional: when true, no action buttons are shown
}

export interface RelationGraphNode {
    id: string;
    label: string;
}

export interface RelationGraphEdge {
    id: string;
    source: string;
    target: string;
    label: string; // relation type
}

export interface RelationGraphPreview {
    nodes: RelationGraphNode[];
    edges: RelationGraphEdge[];
    viewOnly?: boolean;
}
