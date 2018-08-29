import { BaseEntity } from './../../shared';

export abstract class Statistic implements BaseEntity {

    public id: number;
    public released = false;        // default value
    public participantsRated: number;
    public participantsUnrated: number;

    constructor() {
    }
}
