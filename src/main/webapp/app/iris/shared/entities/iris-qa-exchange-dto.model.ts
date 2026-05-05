import { BaseEntity } from 'app/foundation/model/base-entity';

export class QAExchangeDTO implements BaseEntity {
    id?: number;
    question: string;
    answer: string;
    reasoning: string;
}
