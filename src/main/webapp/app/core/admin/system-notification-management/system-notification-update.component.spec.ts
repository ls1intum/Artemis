/**
 * Vitest tests for SystemNotificationManagementUpdateComponent.
 * Tests the create/update form for system notifications including
 * form validation and date constraints.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { SystemNotificationManagementUpdateComponent } from 'app/core/admin/system-notification-management/system-notification-management-update.component';
import { SystemNotification, SystemNotificationType } from 'app/core/shared/entities/system-notification.model';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { AdminSystemNotificationService } from 'app/core/notification/system-notification/admin-system-notification.service';

describe('SystemNotificationManagementUpdateComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<SystemNotificationManagementUpdateComponent>;
    let component: SystemNotificationManagementUpdateComponent;
    let adminService: AdminSystemNotificationService;

    /** Sample notification data loaded from parent route */
    const testNotification = {
        id: 1,
        title: 'test',
        type: 'INFO',
        notificationDate: dayjs(),
        expireDate: dayjs().add(1, 'hour'),
    } as SystemNotification;

    /** Mock activated route with parent data containing notification */
    const mockRoute = {
        parent: {
            data: of({ notification: testNotification }),
        },
    } as unknown as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, SystemNotificationManagementUpdateComponent],
            providers: [
                { provide: ActivatedRoute, useValue: mockRoute },
                MockProvider(ArtemisNavigationUtilService),
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(SystemNotificationManagementUpdateComponent, {
                set: {
                    imports: [ReactiveFormsModule, FaIconComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FormDateTimePickerComponent)],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(SystemNotificationManagementUpdateComponent);
        component = fixture.componentInstance;
        adminService = TestBed.inject(AdminSystemNotificationService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should navigate back when cancel button is clicked', async () => {
        const goToOverviewSpy = vi.spyOn(component, 'goToOverview');
        fixture.detectChanges();

        const cancelButton = fixture.debugElement.nativeElement.querySelector('#cancelButton');
        cancelButton.click();

        await vi.waitFor(() => {
            expect(goToOverviewSpy).toHaveBeenCalledOnce();
        });
    });

    it('should call save when save button is clicked', async () => {
        const saveSpy = vi.spyOn(component, 'save');
        vi.spyOn(adminService, 'update').mockReturnValue(of(new HttpResponse<SystemNotification>()));
        fixture.detectChanges();

        const saveButton = fixture.debugElement.nativeElement.querySelector('#saveButton');
        saveButton.click();

        await vi.waitFor(() => {
            expect(saveSpy).toHaveBeenCalledOnce();
        });
    });

    it.each([
        { name: 'title', exampleValue: 'A title' },
        { name: 'type', exampleValue: SystemNotificationType.INFO },
        { name: 'notificationDate', exampleValue: dayjs() },
        { name: 'expireDate', exampleValue: dayjs() },
    ])('should require $name field', ({ name, exampleValue }) => {
        fixture.detectChanges();

        const control = component.form.get(name);

        // Field should be required when empty
        control?.setValue(null);
        fixture.detectChanges();
        expect(control?.errors?.['required']).toBe(true);

        // Field should pass validation when value is provided
        control?.setValue(exampleValue);
        fixture.detectChanges();
        expect(control?.errors?.['required']).toBeUndefined();
    });

    it('should ensure notification date is before expire date', async () => {
        const saveSpy = vi.spyOn(component, 'save');
        fixture.detectChanges();

        // Set invalid dates: notification date after expire date
        component.form.get('notificationDate')?.setValue(dayjs());
        component.form.get('expireDate')?.setValue(dayjs().subtract(1, 'hour'));
        fixture.detectChanges();

        const saveButton = fixture.debugElement.nativeElement.querySelector('#saveButton');
        saveButton.click();

        await vi.waitFor(
            () => {
                // Save should not be called because dates are invalid
                expect(saveSpy).not.toHaveBeenCalled();
            },
            { timeout: 100 },
        );

        // Fix the dates: expire date after notification date
        component.form.get('expireDate')?.setValue(dayjs().add(1, 'hour'));
        fixture.detectChanges();
        saveButton.click();

        await vi.waitFor(() => {
            expect(saveSpy).toHaveBeenCalledOnce();
        });
    });
});
