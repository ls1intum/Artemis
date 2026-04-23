import { ActivatedRoute, ActivatedRouteSnapshot, Data } from '@angular/router';

import { getRouteData } from 'app/shared/route/getRouteData';

function createSnapshot(data: Data, parentPathFromRoot: ActivatedRouteSnapshot[] = []): ActivatedRouteSnapshot {
    const snapshot = { data } as ActivatedRouteSnapshot;
    Object.defineProperty(snapshot, 'pathFromRoot', {
        value: [...parentPathFromRoot, snapshot],
    });
    return snapshot;
}

function createRouteSnapshot(data: Data, parentPathFromRoot: ActivatedRouteSnapshot[] = []): ActivatedRoute {
    const snapshot = createSnapshot(data, parentPathFromRoot);
    return { snapshot } as ActivatedRoute;
}

describe('getRouteData', () => {
    it('should return data from the current route', () => {
        const route = createRouteSnapshot({ courseId: 42 });

        const routeData = getRouteData<number>(route, 'courseId');

        expect(routeData).toBe(42);
    });

    it('should return data from a parent route when the current route does not contain the key', () => {
        const parentSnapshot = createRouteSnapshot({ examId: 21 }).snapshot;
        const currentRoute = createRouteSnapshot({}, parentSnapshot.pathFromRoot);

        const routeData = getRouteData<number>(currentRoute, 'examId');

        expect(routeData).toBe(21);
    });

    it('should prefer data from the closest route when parent and current route contain the same key', () => {
        const parentSnapshot = createRouteSnapshot({ title: 'Parent title' }).snapshot;
        const currentRoute = createRouteSnapshot({ title: 'Current title' }, parentSnapshot.pathFromRoot);

        const routeData = getRouteData<string>(currentRoute, 'title');

        expect(routeData).toBe('Current title');
    });

    it('should return undefined when the data key does not exist in the route hierarchy', () => {
        const parentSnapshot = createRouteSnapshot({ examId: 7 }).snapshot;
        const currentRoute = createRouteSnapshot({}, parentSnapshot.pathFromRoot);

        const routeData = getRouteData<number>(currentRoute, 'courseId');

        expect(routeData).toBeUndefined();
    });

    it('should return undefined when the route explicitly contains the key with an undefined value', () => {
        const route = createRouteSnapshot({ courseId: undefined });

        const routeData = getRouteData<number>(route, 'courseId');

        expect(routeData).toBeUndefined();
    });
});
