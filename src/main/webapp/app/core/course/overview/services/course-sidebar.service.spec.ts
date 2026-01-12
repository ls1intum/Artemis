import { TestBed } from '@angular/core/testing';

import { CourseSidebarService } from 'app/core/course/overview/services/course-sidebar.service';

describe('CourseSidebarService', () => {
    let service: CourseSidebarService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(CourseSidebarService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should emit closeSidebar event', () => {
        const emitSpy = jest.spyOn(service.closeSidebar$, 'emit');
        service.closeSidebar();
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should emit openSidebar event', () => {
        const emitSpy = jest.spyOn(service.openSidebar$, 'emit');
        service.openSidebar();
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should emit toggleSidebar event', () => {
        const emitSpy = jest.spyOn(service.toggleSidebar$, 'emit');
        service.toggleSidebar();
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should emit reloadSidebar event', () => {
        const emitSpy = jest.spyOn(service.reloadSidebar$, 'emit');
        service.reloadSidebar();
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should emit events when subscribing', () => {
        const closeSpy = jest.fn();
        const openSpy = jest.fn();
        const toggleSpy = jest.fn();
        const reloadSpy = jest.fn();

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
