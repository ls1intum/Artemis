import { TestBed } from '@angular/core/testing';

import { CourseSidebarService } from 'app/overview/course-sidebar.service';

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

    it('should emit events when subscribing', () => {
        const closeSpy = jest.fn();
        const openSpy = jest.fn();
        const toggleSpy = jest.fn();

        service.closeSidebar$.subscribe(closeSpy);
        service.openSidebar$.subscribe(openSpy);
        service.toggleSidebar$.subscribe(toggleSpy);

        service.closeSidebar();
        service.openSidebar();
        service.toggleSidebar();

        expect(closeSpy).toHaveBeenCalled();
        expect(openSpy).toHaveBeenCalled();
        expect(toggleSpy).toHaveBeenCalled();
    });
});
