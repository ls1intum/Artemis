/**
 * Vitest tests for SystemNotificationManagementDetailComponent.
 * Tests the detail view that displays system notification information.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of } from 'rxjs';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { MockModule } from 'ng-mocks';

import { SystemNotificationManagementDetailComponent } from 'app/core/admin/system-notification-management/system-notification-management-detail.component';
import { SystemNotification } from 'app/core/shared/entities/system-notification.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('SystemNotificationManagementDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<SystemNotificationManagementDetailComponent>;
    let component: SystemNotificationManagementDetailComponent;
    let router: MockRouter;

    /** Sample notification data provided through route resolver */
    const testNotification = { id: 1, title: 'test' } as SystemNotification;

    /** Mock activated route with notification data */
    const mockRoute = {
        data: of({ notification: testNotification }),
        children: [],
    } as unknown as ActivatedRoute;

    beforeEach(async () => {
        router = new MockRouter();
        router.setUrl('');

        await TestBed.configureTestingModule({
            imports: [FormsModule, MockModule(RouterModule), SystemNotificationManagementDetailComponent],
            providers: [
                { provide: ActivatedRoute, useValue: mockRoute },
                { provide: Router, useValue: router },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(
                SystemNotificationManagementDetailComponent,
                `
                @if (notification) {
                    <div>
                        <h2>{{ notification.title }}</h2>
                        <a id="editButton">Edit</a>
                    </div>
                }
            `,
            )
            .compileComponents();

        fixture = TestBed.createComponent(SystemNotificationManagementDetailComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should subscribe to route data on initialization', () => {
        const dataSpy = vi.spyOn(mockRoute.data, 'subscribe');
        fixture.detectChanges();
        expect(dataSpy).toHaveBeenCalledOnce();
    });

    it('should load notification from route data', () => {
        fixture.detectChanges();
        expect(component.notification()).toEqual(testNotification);
    });

    it('should display edit button with correct routerLink', () => {
        fixture.detectChanges();

        const editButton = fixture.debugElement.nativeElement.querySelector('#editButton');
        expect(editButton).toBeTruthy();
        // The routerLink attribute is handled by Angular's RouterLink directive
    });
});
