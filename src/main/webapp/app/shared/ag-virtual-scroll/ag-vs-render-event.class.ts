export class AgVsRenderEvent<T> {
    public items: T[];
    public startIndex: number;
    public endIndex: number;
    public length: number;

    constructor(obj: Partial<AgVsRenderEvent<T>>) {
        Object.assign(this, obj);
    }
}
