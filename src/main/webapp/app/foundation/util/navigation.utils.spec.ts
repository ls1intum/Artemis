import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { Location } from '@angular/common';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ArtemisNavigationUtilService, getOnlineEditorLink } from 'app/foundation/util/navigation.utils';
import { MockRouter } from 'test/helpers/mocks/mock-router';

describe('Navigation Util Service', () => {
    let service: ArtemisNavigationUtilService;

    const router = new MockRouter();
    router.setUrl('a');

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [
                { provide: Router, useValue: router },
                { provide: Location, useClass: Location },
            ],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(ArtemisNavigationUtilService);
                // @ts-ignore
                service.onFirstPage = true;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        router.navigate.mockRestore();
    });

    it('should go back', () => {
        const backSpy = vi.spyOn(TestBed.inject(Location), 'back');
        // @ts-ignore
        service.onFirstPage = false;

        service.navigateBack([]);

        expect(backSpy).toHaveBeenCalledOnce();
        expect(backSpy).toHaveBeenCalledWith();
    });

    it('should use fallback', () => {
        service.navigateBack(['a', 'b', 'c']);

        expect(router.navigate).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledWith(['a', 'b', 'c']);
    });

    it('should use without optional', () => {
        service.navigateBackWithOptional(['a', 'b', 'c'], undefined);

        expect(router.navigate).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledWith(['a', 'b', 'c']);
    });

    it('should use with optional', () => {
        service.navigateBackWithOptional(['a', 'b', 'c'], 'd');

        expect(router.navigate).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledWith(['a', 'b', 'c', 'd']);
    });

    it('should route correctly', () => {
        const route = ['course-management', 17];
        const queryParam = { filterOption: 30 };
        const urlTreeMock = { path: 'urlTreeMockTestValue' } as unknown as UrlTree;
        const creationMock = vi.spyOn(router, 'createUrlTree');
        creationMock.mockReturnValue(urlTreeMock);
        const serializationMock = vi.spyOn(router, 'serializeUrl');
        serializationMock.mockReturnValue('serializationMockTestValue');
        const windowStub = vi.spyOn(window, 'open').mockImplementation(() => null);

        service.routeInNewTab(['course-management', 17], queryParam);

        expect(creationMock).toHaveBeenCalledWith(route, queryParam);
        expect(creationMock).toHaveBeenCalledOnce();
        expect(serializationMock).toHaveBeenCalledWith(urlTreeMock);
        expect(windowStub).toHaveBeenCalledWith('serializationMockTestValue', '_blank');
    });
});

describe('getOnlineEditorLink', () => {
    it('should open the participation in the code editor of the course management', () => {
        expect(getOnlineEditorLink(10, 1, 3)).toEqual(['/course-management', '10', 'programming-exercises', '1', 'code-editor', 'participations', '3']);
    });

    it('should open the participation of an exam exercise below its exam and exercise group', () => {
        expect(getOnlineEditorLink(10, 1, 3, 8, 9)).toEqual([
            '/course-management',
            '10',
            'exams',
            '9',
            'exercise-groups',
            '8',
            'programming-exercises',
            '1',
            'code-editor',
            'participations',
            '3',
        ]);
    });

    it('should never address the student route, which shows only the own participation of the viewer', () => {
        expect(getOnlineEditorLink(10, 1, 3)).not.toContain('courses');
        expect(getOnlineEditorLink(10, 1, 3, 8, 9)).not.toContain('courses');
    });
});
