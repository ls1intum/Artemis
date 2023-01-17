import { BaseEntity } from 'app/shared/model/base-entity';

export class QuizGroup implements BaseEntity {
    public id: number;
    public name: string;

    constructor(name: string) {
        this.name = name;
    }
}
