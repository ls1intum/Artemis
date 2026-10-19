import { hydrate } from 'app/foundation/util/deep-clone.util';
/** Instantiated with a full object and populated via Object.assign in the constructor, hence the definite-assignment (!) markers. */
export class VirtualScrollRenderEvent<T> {
    public items!: T[];
    public startIndex!: number;
    public endIndex!: number;
    public length!: number;

    constructor(obj: Partial<VirtualScrollRenderEvent<T>>) {
        hydrate(this, obj);
    }
}
