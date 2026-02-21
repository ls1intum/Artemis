import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';

import { CourseSidebarService } from 'app/core/course/overview/services/course-sidebar.service';

describe('CourseSidebarService', () => {
    setupTestBed({ zoneless: true });

    let service: CourseSidebarService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(CourseSidebarService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should emit closeSidebar event', () => {
        const emitSpy = vi.spyOn(service.closeSidebar$, 'emit');
        service.closeSidebar();
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should emit openSidebar event', () => {
        const emitSpy = vi.spyOn(service.openSidebar$, 'emit');
        service.openSidebar();
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should emit toggleSidebar event', () => {
        const emitSpy = vi.spyOn(service.toggleSidebar$, 'emit');
        service.toggleSidebar();
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should emit reloadSidebar event', () => {
        const emitSpy = vi.spyOn(service.reloadSidebar$, 'emit');
        service.reloadSidebar();
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should emit events when subscribing', () => {
        const closeSpy = vi.fn();
        const openSpy = vi.fn();
        const toggleSpy = vi.fn();
        const reloadSpy = vi.fn();

        service.closeSidebar$.subscribe(closeSpy);
        service.openSidebar$.subscribe(openSpy);
        service.toggleSidebar$.subscribe(toggleSpy);
        service.reloadSidebar$.subscribe(reloadSpy);

        service.closeSidebar();
        service.openSidebar();
        service.toggleSidebar();
        service.reloadSidebar();

        expect(closeSpy).toHaveBeenCalled();
        expect(openSpy).toHaveBeenCalled();
        expect(toggleSpy).toHaveBeenCalled();
        expect(reloadSpy).toHaveBeenCalled();
    });
});
