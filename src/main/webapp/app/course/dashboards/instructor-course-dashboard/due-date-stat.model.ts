/**
 * Model to represent a number that depends on an exercise due-date, like
 * numberOfSubmissions. Has an inTime and late member.
 */
export class DueDateStat {
    public inTime = 0;
    public late = 0;

    constructor() {}

    /**
     * Computed property to get the total number of
     * both properties, inTime and late members.
     */
    public get total() {
        return this.inTime + this.late;
    }
}
