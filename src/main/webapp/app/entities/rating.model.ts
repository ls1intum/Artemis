import { Result } from 'app/entities/result.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class Rating implements BaseEntity {
    public id?: number;
    public result?: Result;
    public rating?: number;

    constructor(result: Result | undefined, rating: number) {
        this.result = result;
        this.rating = rating;
    }
}
