export interface IStatisticCounter {
    id?: number;
    ratedCounter?: number;
    unRatedCounter?: number;
}

export class StatisticCounter implements IStatisticCounter {
    constructor(public id?: number, public ratedCounter?: number, public unRatedCounter?: number) {}
}
