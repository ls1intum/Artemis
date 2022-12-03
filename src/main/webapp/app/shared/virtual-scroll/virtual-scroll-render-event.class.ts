export class VirtualScrollRenderEvent<T> {
    public items: T[];
    public startIndex: number;
    public endIndex: number;
    public length: number;

    constructor(obj: Partial<VirtualScrollRenderEvent<T>>) {
        Object.assign(this, obj);
    }
}
