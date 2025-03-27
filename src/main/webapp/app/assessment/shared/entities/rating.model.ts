import { BaseEntity } from 'app/shared/model/base-entity';
import { Result } from 'app/exercise/shared/entities/result/result.model';

export class Rating implements BaseEntity {
    public id?: number;
    public result?: Result;
    public rating?: number;

    constructor(result: Result | undefined, rating: number) {
        this.result = result;
        this.rating = rating;
    }
}
