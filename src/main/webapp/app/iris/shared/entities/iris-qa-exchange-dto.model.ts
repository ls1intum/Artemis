import { BaseEntity } from 'app/foundation/model/base-entity';

/**
 * A single question-answer exchange, including the reasoning behind the answer.
 */
export interface QAExchangeDTO extends BaseEntity {
    id?: number;
    question: string;
    answer: string;
    reasoning: string;
}
