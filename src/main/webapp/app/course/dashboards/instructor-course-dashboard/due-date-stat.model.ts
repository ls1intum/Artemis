
export class DueDateStat {

    public inTime = 0;
    public late = 0;

    constructor() {}

    public get total() {
        return this.inTime + this.late;
    }
}
