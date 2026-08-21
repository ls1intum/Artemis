import { TestBed } from '@angular/core/testing';
import { NavigationStart, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, Subscription } from 'rxjs';

import { PageActivityService } from 'app/foundation/service/page-activity.service';

describe('PageActivityService', () => {
    let service: PageActivityService;
    let routerEvents: Subject<unknown>;
    let pageLeavingSpy: ReturnType<typeof vi.fn<() => void>>;
    let subscription: Subscription;

    beforeEach(() => {
        routerEvents = new Subject<unknown>();
        pageLeavingSpy = vi.fn();

        TestBed.configureTestingModule({
            providers: [PageActivityService, { provide: Router, useValue: { events: routerEvents.asObservable() } }],
        });

        service = TestBed.inject(PageActivityService);
        subscription = service.pageLeaving$.subscribe(pageLeavingSpy);
    });

    afterEach(() => {
        subscription.unsubscribe();
        vi.restoreAllMocks();
    });

    it('should emit when a router navigation starts', () => {
        routerEvents.next(new NavigationStart(1, '/courses'));

        expect(pageLeavingSpy).toHaveBeenCalledOnce();
        expect(pageLeavingSpy).toHaveBeenCalledWith(undefined);
    });

    it('should emit when the window loses focus', () => {
        window.dispatchEvent(new Event('blur'));

        expect(pageLeavingSpy).toHaveBeenCalledOnce();
        expect(pageLeavingSpy).toHaveBeenCalledWith(undefined);
    });

    it('should emit before the window unloads', () => {
        window.dispatchEvent(new Event('beforeunload'));

        expect(pageLeavingSpy).toHaveBeenCalledOnce();
        expect(pageLeavingSpy).toHaveBeenCalledWith(undefined);
    });

    it('should emit when the page is hidden from the session history', () => {
        window.dispatchEvent(new Event('pagehide'));

        expect(pageLeavingSpy).toHaveBeenCalledOnce();
        expect(pageLeavingSpy).toHaveBeenCalledWith(undefined);
    });

    it('should emit when the document becomes hidden', () => {
        vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');

        document.dispatchEvent(new Event('visibilitychange'));

        expect(pageLeavingSpy).toHaveBeenCalledOnce();
        expect(pageLeavingSpy).toHaveBeenCalledWith(undefined);
    });

    it('should not emit when the document stays visible', () => {
        vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');

        document.dispatchEvent(new Event('visibilitychange'));

        expect(pageLeavingSpy).not.toHaveBeenCalled();
    });
});
