import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import dayjs from 'dayjs/esm';

export interface ChatMessage {
    id: string;
    content: string;
    isUser: boolean;
    timestamp: Date;
    canCreateCompetencies?: boolean;
    suggestedCompetencies?: CompetencyDraft[];
}

export interface CompetencyDraft {
    title: string;
    description: string;
    taxonomy: CompetencyTaxonomy;
    masteryThreshold: number;
    optional: boolean;
    softDueDate?: dayjs.Dayjs;
}
