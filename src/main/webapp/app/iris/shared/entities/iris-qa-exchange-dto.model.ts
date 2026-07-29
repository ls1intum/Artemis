import { BaseEntity } from 'app/foundation/model/base-entity';

export interface QAExchangeDTO extends BaseEntity {
    id?: number;
    question: string;
    answer: string;
    reasoning: string;
}
