import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';

export interface ChatMessage {
    id: string;
    content: string;
    isUser: boolean;
    timestamp: Date;
    competencyPreviews?: CompetencyPreview[];
    competencyCreated?: boolean;
    planPending?: boolean;
    planApproved?: boolean;
}

export interface CompetencyPreview {
    title: string;
    description: string;
    taxonomy: CompetencyTaxonomy;
    icon?: string;
    competencyId?: number; // Optional: present when updating existing competency
    viewOnly?: boolean; // Optional: when true, no action buttons are shown
}
