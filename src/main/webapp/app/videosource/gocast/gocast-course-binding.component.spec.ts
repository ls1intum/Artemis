import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { vi } from 'vitest';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GocastCourseBindingComponent } from './gocast-course-binding.component';

describe('GocastCourseBindingComponent', () => {
    let fixture: ComponentFixture<GocastCourseBindingComponent>;
    let component: GocastCourseBindingComponent;
    let http: HttpTestingController;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [GocastCourseBindingComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        http = TestBed.inject(HttpTestingController);
        fixture = TestBed.createComponent(GocastCourseBindingComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 37);
        fixture.detectChanges();
    });

    afterEach(() => {
        http.verify();
        vi.restoreAllMocks();
    });

    it('shows the unconfigured state without a connection action', async () => {
        http.expectOne('api/videosource/courses/37/binding').flush({ available: false, status: 'UNLINKED' });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('artemisApp.gocast.unavailable');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.gocast.connect');
    });

    it('starts once and navigates directly to the approval page', async () => {
        http.expectOne('api/videosource/courses/37/binding').flush({ available: true, status: 'UNLINKED' });
        await fixture.whenStable();
        const navigate = vi.spyOn(component as never, 'navigateToApproval' as never).mockImplementation(() => undefined);

        component.connect();
        component.connect();
        http.expectOne('api/videosource/courses/37/binding/approval').flush({ approvalUrl: 'https://live.example/approve/id', expiresAt: '2026-09-05T03:15:00Z' });

        expect(navigate).toHaveBeenCalledWith('https://live.example/approve/id');
    });

    it('shows a warning when signed-in access is required', async () => {
        http.expectOne('api/videosource/courses/37/binding').flush({
            available: true,
            status: 'ACTIVE',
            courseId: 23,
            courseName: 'Signed-in algorithms seminar',
            courseVisibility: 'loggedin',
        });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.isRestricted()).toBe(true);
        expect(fixture.nativeElement.textContent).toContain('artemisApp.gocast.restrictedWarning');
    });

    it('does not show a restricted-access warning for a link-public course', async () => {
        http.expectOne('api/videosource/courses/37/binding').flush({
            available: true,
            status: 'ACTIVE',
            courseId: 23,
            courseName: 'Algorithms seminar',
            courseVisibility: 'hidden',
        });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.isRestricted()).toBe(false);
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.gocast.restrictedWarning');
    });

    it('shows a retry action when the initial load fails', async () => {
        http.expectOne('api/videosource/courses/37/binding').flush({ message: 'unavailable' }, { status: 503, statusText: 'Service Unavailable' });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="gocast-load-retry"]')).not.toBeNull();
        component.refresh();
        http.expectOne('api/videosource/courses/37/binding').flush({ available: true, status: 'UNLINKED' });
    });

    it('formats a pending approval expiry for the current locale', async () => {
        const rawExpiry = '2026-09-05T03:15:00Z';
        http.expectOne('api/videosource/courses/37/binding').flush({ available: true, status: 'PENDING', expiresAt: rawExpiry });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).not.toContain(rawExpiry);
    });

    it('keeps the connection visible when refresh is unavailable', async () => {
        http.expectOne('api/videosource/courses/37/binding').flush({
            available: true,
            status: 'ACTIVE',
            courseId: 23,
            courseName: 'Introduction to programming',
            courseVisibility: 'loggedin',
        });
        await fixture.whenStable();
        fixture.detectChanges();

        component.refresh();
        http.expectOne('api/videosource/courses/37/binding').flush({ message: 'unavailable' }, { status: 503, statusText: 'Service Unavailable' });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('Introduction to programming');
        expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    });

    it('keeps the connected view after a failed unlink and clears it after success', async () => {
        http.expectOne('api/videosource/courses/37/binding').flush({
            available: true,
            status: 'ACTIVE',
            courseId: 23,
            courseName: 'Introduction to programming',
            courseVisibility: 'loggedin',
        });
        await fixture.whenStable();

        component.disconnect();
        http.expectOne('api/videosource/courses/37/binding').flush({ message: 'unavailable' }, { status: 503, statusText: 'Service Unavailable' });
        await fixture.whenStable();
        expect(component.binding()?.courseName).toBe('Introduction to programming');

        component.disconnect();
        http.expectOne('api/videosource/courses/37/binding').flush(null);
        await fixture.whenStable();
        expect(component.binding()).toEqual({ available: true, status: 'UNLINKED' });
    });

    it('does not show the previous course while a changed course input loads', async () => {
        http.expectOne('api/videosource/courses/37/binding').flush({ available: true, status: 'ACTIVE', courseName: 'Old course', courseVisibility: 'public' });
        await fixture.whenStable();
        fixture.componentRef.setInput('courseId', 38);
        fixture.detectChanges();

        expect(component.binding()).toBeUndefined();
        http.expectOne('api/videosource/courses/38/binding').flush({ available: true, status: 'UNLINKED' });
        await fixture.whenStable();
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).not.toContain('Old course');
    });

    it('closes an open disconnect confirmation when the course changes', async () => {
        http.expectOne('api/videosource/courses/37/binding').flush({ available: true, status: 'ACTIVE', courseName: 'Old course', courseVisibility: 'public' });
        await fixture.whenStable();
        component.showDisconnectDialog();
        expect(component.disconnectDialogVisible()).toBe(true);

        fixture.componentRef.setInput('courseId', 38);
        fixture.detectChanges();

        expect(component.disconnectDialogVisible()).toBe(false);
        http.expectOne('api/videosource/courses/38/binding').flush({ available: true, status: 'UNLINKED' });
    });

    it('ignores a late approval response after the course changes', async () => {
        http.expectOne('api/videosource/courses/37/binding').flush({ available: true, status: 'UNLINKED' });
        await fixture.whenStable();
        const navigate = vi.spyOn(component as never, 'navigateToApproval' as never).mockImplementation(() => undefined);
        component.connect();
        const oldApproval = http.expectOne('api/videosource/courses/37/binding/approval');

        fixture.componentRef.setInput('courseId', 38);
        fixture.detectChanges();
        http.expectOne('api/videosource/courses/38/binding').flush({ available: true, status: 'UNLINKED' });
        oldApproval.flush({ approvalUrl: 'https://live.example/approve/old', expiresAt: '2026-09-05T03:15:00Z' });
        await fixture.whenStable();

        expect(navigate).not.toHaveBeenCalled();
        expect(component.action()).toBeUndefined();
    });

    it('ignores a late approval error after the course changes', async () => {
        http.expectOne('api/videosource/courses/37/binding').flush({ available: true, status: 'UNLINKED' });
        await fixture.whenStable();
        component.connect();
        const oldApproval = http.expectOne('api/videosource/courses/37/binding/approval');

        fixture.componentRef.setInput('courseId', 38);
        fixture.detectChanges();
        http.expectOne('api/videosource/courses/38/binding').flush({ available: true, status: 'UNLINKED' });
        oldApproval.flush({ message: 'old failure' }, { status: 503, statusText: 'Service Unavailable' });
        await fixture.whenStable();

        expect(component.error()).toBe(false);
    });

    it('keeps the new course after a late disconnect response and closes the old confirmation', async () => {
        http.expectOne('api/videosource/courses/37/binding').flush({ available: true, status: 'ACTIVE', courseName: 'Old course', courseVisibility: 'public' });
        await fixture.whenStable();
        component.showDisconnectDialog();
        component.disconnect();
        const oldDisconnect = http.expectOne('api/videosource/courses/37/binding');

        fixture.componentRef.setInput('courseId', 38);
        fixture.detectChanges();
        expect(component.disconnectDialogVisible()).toBe(false);
        http.expectOne('api/videosource/courses/38/binding').flush({ available: true, status: 'ACTIVE', courseName: 'New course', courseVisibility: 'public' });
        oldDisconnect.flush(null);
        await fixture.whenStable();

        expect(component.binding()?.courseName).toBe('New course');
    });

    it('ignores a late disconnect error after the course changes', async () => {
        http.expectOne('api/videosource/courses/37/binding').flush({ available: true, status: 'ACTIVE', courseName: 'Old course', courseVisibility: 'public' });
        await fixture.whenStable();
        component.disconnect();
        const oldDisconnect = http.expectOne('api/videosource/courses/37/binding');

        fixture.componentRef.setInput('courseId', 38);
        fixture.detectChanges();
        http.expectOne('api/videosource/courses/38/binding').flush({ available: true, status: 'ACTIVE', courseName: 'New course', courseVisibility: 'public' });
        oldDisconnect.flush({ message: 'old failure' }, { status: 503, statusText: 'Service Unavailable' });
        await fixture.whenStable();

        expect(component.error()).toBe(false);
        expect(component.binding()?.courseName).toBe('New course');
    });
});
