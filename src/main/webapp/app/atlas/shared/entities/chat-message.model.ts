import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import dayjs from 'dayjs/esm';

export interface ChatMessage {
    id: string;
    content: string;
    isUser: boolean;
    timestamp: Date;
    canCreateCompetencies?: boolean;
    suggestedCompetencies?: CompetencyDraft[];
    competencyPreview?: CompetencyPreview;
    competencyCreated?: boolean;
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
