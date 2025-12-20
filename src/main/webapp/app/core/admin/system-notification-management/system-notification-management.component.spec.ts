import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { SystemNotificationManagementComponent } from 'app/core/admin/system-notification-management/system-notification-management.component';
import { SystemNotification } from 'app/core/shared/entities/system-notification.model';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { MockRouter } from '../../../../../../test/javascript/spec/helpers/mocks/mock-router';
import { MockRouterLinkDirective } from '../../../../../../test/javascript/spec/helpers/mocks/directive/mock-router-link.directive';
import '@angular/localize/init';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AlertService } from 'app/shared/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../../../test/javascript/spec/helpers/mocks/service/mock-account.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../../../../test/javascript/spec/helpers/mocks/service/mock-translate.service';

describe('SystemNotificationManagementComponent', () => {
    let managementComponentFixture: ComponentFixture<SystemNotificationManagementComponent>;
    let managementComponent: SystemNotificationManagementComponent;
    let router: MockRouter;

    const route = {
        data: of({ pagingParams: {} }),
        children: [],
    } as any as ActivatedRoute;

    beforeEach(async () => {
        router = new MockRouter();
        router.setUrl('');

        await TestBed.configureTestingModule({
            declarations: [
                SystemNotificationManagementComponent,
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
                MockRouterLinkDirective,
                MockDirective(DeleteButtonDirective),
                MockDirective(SortDirective),
                MockComponent(ItemCountComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: router },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        managementComponentFixture = TestBed.createComponent(SystemNotificationManagementComponent);
        managementComponent = managementComponentFixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
        router.navigate.mockRestore();
    });

    it('navigate to the details page of system notification if details is clicked', fakeAsync(() => {
        const notification = new SystemNotification();
        notification.id = 1;
        notification.expireDate = dayjs();
        notification.notificationDate = dayjs();
        managementComponent.notifications = [notification];
        managementComponentFixture.changeDetectorRef.detectChanges();

        const button = managementComponentFixture.debugElement.nativeElement.querySelector('#viewButton');
        button.click();

        tick();
        expect(router.navigateByUrl).toHaveBeenCalledOnce();
    }));

    it('navigate to the edit page of system notification if details is clicked', fakeAsync(() => {
        const notification = new SystemNotification();
        notification.id = 2;
        notification.expireDate = dayjs();
        notification.notificationDate = dayjs();
        managementComponent.notifications = [notification];
        managementComponentFixture.changeDetectorRef.detectChanges();

        const button = managementComponentFixture.debugElement.nativeElement.querySelector('#editButton');
        button.click();

        tick();
        expect(router.navigateByUrl).toHaveBeenCalledOnce();
    }));

    it('should unsubscribe on destroy', () => {
        const routeDataSpy = jest.spyOn(managementComponent.routeData, 'unsubscribe');
        managementComponentFixture.destroy();
        expect(routeDataSpy).toHaveBeenCalledOnce();
    });

    it('should transition on page change', fakeAsync(() => {
        managementComponent.notifications = [];
        managementComponentFixture.changeDetectorRef.detectChanges();

        const pagination = managementComponentFixture.debugElement.nativeElement.querySelector('#pagination');
        pagination.dispatchEvent(new Event('pageChange'));

        tick();
        expect(router.navigate.mock.calls[0][0]).toEqual(['/admin/system-notification-management']);
    }));
});
