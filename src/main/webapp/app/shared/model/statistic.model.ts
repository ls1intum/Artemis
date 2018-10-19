export interface IStatistic {
    id?: number;
    released?: boolean;
    participantsRated?: number;
    participantsUnrated?: number;
}

export class Statistic implements IStatistic {
    constructor(public id?: number, public released?: boolean, public participantsRated?: number, public participantsUnrated?: number) {
        this.released = this.released || false;
    }
}
