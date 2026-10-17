/**
 * Vitest tests for SystemNotificationManagementUpdateComponent.
 * Tests the create/update form for system notifications including
 * form validation and date constraints.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockPipe, MockProvider } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import {
    TumAetUiButtonDirective,
    TumAetUiCheckboxComponent,
    TumAetUiDatePickerComponent,
    TumAetUiInputDirective,
    TumAetUiMessageComponent,
    TumAetUiSelectComponent,
} from '@tumaet/ui-angular';
import { SystemNotificationManagementUpdateComponent } from 'app/admin/system-notification-management/system-notification-management-update.component';
import { SystemNotification, SystemNotificationType } from 'app/admin/system-notification-management/system-notification.model';
import { DatePickerControlDirective } from 'app/shared-ui/date-picker-control/date-picker-control.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { AdminSystemNotificationService } from 'app/core/notification/system-notification/admin-system-notification.service';

describe('SystemNotificationManagementUpdateComponent', () => {
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
            imports: [ReactiveFormsModule, SystemNotificationManagementUpdateComponent, FontAwesomeTestingModule],
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
                    imports: [
                        ReactiveFormsModule,
                        FormsModule,
                        FaIconComponent,
                        MockPipe(ArtemisTranslatePipe),
                        TumAetUiDatePickerComponent,
                        DatePickerControlDirective,
                        TumAetUiSelectComponent,
                        TumAetUiCheckboxComponent,
                        TumAetUiInputDirective,
                        TumAetUiButtonDirective,
                        TumAetUiMessageComponent,
                    ],
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

    describe('date pickers', () => {
        const pickerInput = (id: string) => fixture.nativeElement.querySelector(`input#${id}`) as HTMLInputElement;
        const type = (id: string, text: string) => {
            const input = pickerInput(id);
            input.value = text;
            input.dispatchEvent(new Event('input'));
            fixture.detectChanges();
        };
        const saveButton = () => fixture.nativeElement.querySelector('#saveButton') as HTMLButtonElement;

        it('should show the dates of the loaded notification', () => {
            fixture.detectChanges();

            expect(pickerInput('notificationDate').value).toBe(testNotification.notificationDate!.format('DD.MM.YYYY HH:mm'));
            expect(pickerInput('expireDate').value).toBe(testNotification.expireDate!.format('DD.MM.YYYY HH:mm'));
        });

        it('should name each date field by a visible label bound to its input', () => {
            fixture.detectChanges();

            for (const id of ['notificationDate', 'expireDate']) {
                expect(fixture.nativeElement.querySelector(`label[for="${id}"]`)).not.toBeNull();
                expect(pickerInput(id)).not.toBeNull();
            }
        });

        it('should write a typed date to the form control', () => {
            fixture.detectChanges();

            type('notificationDate', '01.02.2030 10:15');

            expect(component.form.get('notificationDate')?.value?.format('YYYY-MM-DD HH:mm')).toBe('2030-02-01 10:15');
        });

        it('should block saving while a date field holds text that is not a date', () => {
            fixture.detectChanges();
            expect(saveButton().disabled).toBe(false);

            type('expireDate', '13.06.');

            expect(component.form.get('expireDate')?.hasError('invalidDate')).toBe(true);
            expect(saveButton().disabled).toBe(true);

            type('expireDate', '01.02.2099 10:15');

            expect(saveButton().disabled).toBe(false);
        });

        it('should explain and mark the expire date when it is not after the notification date', () => {
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('#expireDate-order-error')).toBeNull();

            component.form.get('notificationDate')?.setValue(dayjs());
            component.form.get('expireDate')?.setValue(dayjs().subtract(1, 'hour'));
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('#expireDate-order-error')).not.toBeNull();
            expect(pickerInput('expireDate').getAttribute('aria-invalid')).toBe('true');
            expect(pickerInput('expireDate').getAttribute('aria-describedby')).toBe('expireDate-order-error');
            expect(saveButton().disabled).toBe(true);
        });
    });
});
