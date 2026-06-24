import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { GocastCourseBindingComponent } from './gocast-course-binding.component';
import { GocastService } from './gocast.service';
import { GocastBindingWithApproval, GocastCourse } from './gocast.model';

describe('GocastCourseBindingComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<GocastCourseBindingComponent>;
    let component: GocastCourseBindingComponent;
    let gocastService: GocastService;
    let alertService: AlertService;

    const mockCourses: GocastCourse[] = [
        { id: 1, name: 'Eidi', slug: 'eidi', year: 2026, teachingTerm: 'W', vodEnabled: true, visibility: 'loggedin' },
        { id: 2, name: 'GDB', slug: 'gdb', year: 2026, teachingTerm: 'W', vodEnabled: false, visibility: 'public' },
    ];

    const pendingBindingWithApproval: GocastBindingWithApproval = {
        binding: {
            courseId: 10,
            gocastCourseId: 1,
            gocastCourseSlug: 'eidi',
            status: 'PENDING',
        },
        approvalUrl: 'https://tum.live/admin/course/1/integration/confirm?service=99&redirect=https://artemis.tum.de/callback',
    };

    const activeBindingWithApproval: GocastBindingWithApproval = {
        binding: {
            courseId: 10,
            gocastCourseId: 1,
            gocastCourseSlug: 'eidi',
            status: 'ACTIVE',
        },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [GocastCourseBindingComponent, FormsModule, TranslateDirective, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(GocastService), MockProvider(AlertService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        gocastService = TestBed.inject(GocastService);
        alertService = TestBed.inject(AlertService);

        // Default stubs — getBinding returns GocastBindingWithApproval now
        vi.spyOn(gocastService, 'listAdministeredTumLiveCourses').mockReturnValue(of(mockCourses));
        vi.spyOn(gocastService, 'getBinding').mockReturnValue(throwError(() => ({ status: 404 })));
    });

    function createComponent(courseId = 10): void {
        fixture = TestBed.createComponent(GocastCourseBindingComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', courseId);
        fixture.detectChanges();
    }

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load administered TUM Live courses on init', () => {
        createComponent();
        expect(gocastService.listAdministeredTumLiveCourses).toHaveBeenCalledWith(10);
        expect(component.tumLiveCourses()).toHaveLength(2);
        expect(component.tumLiveCourses()[0].name).toBe('Eidi');
    });

    it('should show the course dropdown when there is no binding', () => {
        createComponent();
        const select = fixture.nativeElement.querySelector('#gocastCourseSelect');
        expect(select).toBeDefined();
    });

    it('should set selectedGocastCourseId and slug when a course is selected', () => {
        createComponent();

        // Simulate selecting the first course (id=1) via the p-select onChange value
        component.tumLiveCourses.set(mockCourses);
        component.onCourseSelected(1);

        expect(component.selectedGocastCourseId()).toBe(1);
        expect(component.selectedGocastCourseSlug()).toBe('eidi');
    });

    it('should clear the selection when the dropdown is cleared', () => {
        createComponent();
        component.tumLiveCourses.set(mockCourses);
        component.onCourseSelected(1);
        expect(component.selectedGocastCourseId()).toBe(1);

        // p-select [showClear] emits undefined on clear
        component.onCourseSelected(undefined);

        expect(component.selectedGocastCourseId()).toBeUndefined();
        expect(component.selectedGocastCourseSlug()).toBeUndefined();
    });

    it('should create a PENDING binding and store it along with the approvalUrl', () => {
        vi.spyOn(gocastService, 'createBinding').mockReturnValue(of(pendingBindingWithApproval));
        vi.spyOn(alertService, 'success');

        createComponent();
        component.selectedGocastCourseId.set(1);
        component.selectedGocastCourseSlug.set('eidi');
        component.createBinding();

        expect(gocastService.createBinding).toHaveBeenCalledWith(10, 1, 'eidi');
        expect(component.binding()?.status).toBe('PENDING');
        expect(component.approvalUrl()).toBe('https://tum.live/admin/course/1/integration/confirm?service=99&redirect=https://artemis.tum.de/callback');
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.gocast.binding.pendingCreated');
    });

    it('should open the approval URL in a new tab', () => {
        createComponent();
        component.binding.set(pendingBindingWithApproval.binding);
        component.approvalUrl.set('https://tum.live/admin/course/1/integration/confirm?service=99&redirect=https://artemis.tum.de/callback');

        const openSpy = vi.spyOn(window, 'open').mockReturnValue(null);
        component.openApprovalPage();

        expect(openSpy).toHaveBeenCalledWith(
            'https://tum.live/admin/course/1/integration/confirm?service=99&redirect=https://artemis.tum.de/callback',
            '_blank',
            'noopener,noreferrer',
        );
    });

    it('should flip binding to ACTIVE when checkBindingStatus returns ACTIVE', () => {
        vi.spyOn(gocastService, 'getBinding').mockReturnValue(of(activeBindingWithApproval));
        vi.spyOn(alertService, 'success');

        createComponent();
        component.binding.set(pendingBindingWithApproval.binding);
        component.checkBindingStatus();

        expect(component.binding()?.status).toBe('ACTIVE');
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.gocast.binding.active');
    });

    it('should keep binding as PENDING when gocast has not yet confirmed', () => {
        const stillPendingWithApproval: GocastBindingWithApproval = {
            binding: { ...pendingBindingWithApproval.binding, status: 'PENDING' },
            approvalUrl: pendingBindingWithApproval.approvalUrl,
        };
        vi.spyOn(gocastService, 'getBinding').mockReturnValue(of(stillPendingWithApproval));
        vi.spyOn(alertService, 'info');

        createComponent();
        component.binding.set(pendingBindingWithApproval.binding);
        component.checkBindingStatus();

        expect(component.binding()?.status).toBe('PENDING');
        expect(alertService.info).toHaveBeenCalledWith('artemisApp.gocast.binding.stillPending');
    });

    it('should revoke the binding and clear it', () => {
        vi.spyOn(gocastService, 'deleteBinding').mockReturnValue(of(undefined));
        vi.spyOn(alertService, 'success');

        createComponent();
        component.binding.set(activeBindingWithApproval.binding);
        component.revokeBinding();

        expect(gocastService.deleteBinding).toHaveBeenCalledWith(10);
        expect(component.binding()).toBeUndefined();
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.gocast.binding.revoked');
    });

    it('should show an error alert when loading courses fails', () => {
        vi.spyOn(gocastService, 'listAdministeredTumLiveCourses').mockReturnValue(throwError(() => new Error('Network error')));
        vi.spyOn(alertService, 'error');

        createComponent();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.gocast.binding.error.loadCourses');
        expect(component.tumLiveCourses()).toHaveLength(0);
    });

    it('should load existing ACTIVE binding on init', () => {
        // Override getBinding to return an active binding (wrapped in GocastBindingWithApproval)
        vi.spyOn(gocastService, 'getBinding').mockReturnValue(of(activeBindingWithApproval));

        createComponent();

        expect(component.binding()?.status).toBe('ACTIVE');
        // The approval button should not be visible
        const approvalBtn = fixture.nativeElement.querySelector('#openApprovalButton');
        expect(approvalBtn).toBeNull();
    });
});
