import { BaseEntity } from 'app/shared/model/base-entity';

export class Rating implements BaseEntity {
    public id: number;
    public feedback: number;
    public rating: number;

    constructor(id: number, feedback: number, rating: number) {
        this.id = id;
        this.feedback = feedback;
        this.rating = rating;
    }
}
