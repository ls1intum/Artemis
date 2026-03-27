import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariable';

interface TestActivatedRoute extends ActivatedRoute {
    paramMapSubject: BehaviorSubject<ParamMap>;
}

function createActivatedRoute(params: Record<string, string>, parent?: ActivatedRoute): TestActivatedRoute {
    const paramMapSubject = new BehaviorSubject<ParamMap>(convertToParamMap(params));

    return {
        snapshot: {
            paramMap: convertToParamMap(params),
        },
        paramMap: paramMapSubject.asObservable(),
        parent,
        paramMapSubject,
    } as TestActivatedRoute;
}

describe('getNumericPathVariableSignal', () => {
    beforeEach(() => {
        TestBed.configureTestingModule({});
    });

    it('should return the numeric path variable from the current route', () => {
        const route = createActivatedRoute({ courseId: '42' });

        const pathVariableSignal = TestBed.runInInjectionContext(() => getNumericPathVariableSignal(route, 'courseId'));

        expect(pathVariableSignal()).toBe(42);
    });

    it('should return the numeric path variable from a parent route', () => {
        const parentRoute = createActivatedRoute({ courseId: '21' });
        const childRoute = createActivatedRoute({}, parentRoute);

        const pathVariableSignal = TestBed.runInInjectionContext(() => getNumericPathVariableSignal(childRoute, 'courseId'));

        expect(pathVariableSignal()).toBe(21);
    });

    it('should return undefined when the path variable does not exist in the route hierarchy', () => {
        const parentRoute = createActivatedRoute({ examId: '7' });
        const childRoute = createActivatedRoute({}, parentRoute);

        const pathVariableSignal = TestBed.runInInjectionContext(() => getNumericPathVariableSignal(childRoute, 'courseId'));

        expect(pathVariableSignal()).toBeUndefined();
    });

    it('should return undefined when the path variable is not numeric', () => {
        const route = createActivatedRoute({ courseId: 'abc' });

        const pathVariableSignal = TestBed.runInInjectionContext(() => getNumericPathVariableSignal(route, 'courseId'));

        expect(pathVariableSignal()).toBeUndefined();
    });

    it('should return undefined when the path variable is removed after the signal was created', () => {
        const route = createActivatedRoute({ courseId: '42' });

        const pathVariableSignal = TestBed.runInInjectionContext(() => getNumericPathVariableSignal(route, 'courseId'));
        expect(pathVariableSignal()).toBe(42);

        route.paramMapSubject.next(convertToParamMap({}));

        expect(pathVariableSignal()).toBeUndefined();
    });
});
