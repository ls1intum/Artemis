import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, RouterEvent, RouterOutlet } from '@angular/router';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { SystemNotificationManagementComponent } from 'app/admin/system-notification-management/system-notification-management.component';
import { SystemNotification } from 'app/entities/system-notification.model';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';

describe('SystemNotificationManagementComponent', () => {
    let managementComponentFixture: ComponentFixture<SystemNotificationManagementComponent>;
    let managementComponent: SystemNotificationManagementComponent;
    let router: MockRouter;

    const route = {
        data: of({ pagingParams: {} }),
        children: [],
    } as any as ActivatedRoute;

    beforeEach(() => {
        router = new MockRouter();
        router.events = of({ id: 1, url: '' } as RouterEvent);

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                SystemNotificationManagementComponent,
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
                MockRouterLinkDirective,
                MockDirective(RouterOutlet),
                MockDirective(AlertComponent),
                MockDirective(DeleteButtonDirective),
                MockDirective(NgbPagination),
                MockDirective(SortDirective),
                MockComponent(ItemCountComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: router },
            ],
        })
            .compileComponents()
            .then(() => {
                managementComponentFixture = TestBed.createComponent(SystemNotificationManagementComponent);
                managementComponent = managementComponentFixture.componentInstance;
            });
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
        managementComponentFixture.detectChanges();

        const button = managementComponentFixture.debugElement.nativeElement.querySelector('#viewButton');
        button.click();

        tick();
        expect(router.navigateByUrl).toHaveBeenCalledTimes(1);
        expect(router.navigateByUrl.mock.calls[0][0]).toEqual(['./', notification.id]);
    }));

    it('navigate to the edit page of system notification if details is clicked', fakeAsync(() => {
        const notification = new SystemNotification();
        notification.id = 2;
        notification.expireDate = dayjs();
        notification.notificationDate = dayjs();
        managementComponent.notifications = [notification];
        managementComponentFixture.detectChanges();

        const button = managementComponentFixture.debugElement.nativeElement.querySelector('#editButton');
        button.click();

        tick();
        expect(router.navigateByUrl).toHaveBeenCalledTimes(1);
        expect(router.navigateByUrl.mock.calls[0][0]).toEqual(['./', notification.id, 'edit']);
    }));

    it('should unsubscribe on destroy', () => {
        const routeDataSpy = jest.spyOn(managementComponent.routeData, 'unsubscribe');
        managementComponentFixture.destroy();
        expect(routeDataSpy).toHaveBeenCalledTimes(1);
    });

    it('should transition on page change', fakeAsync(() => {
        managementComponent.notifications = [];
        managementComponentFixture.detectChanges();

        const pagination = managementComponentFixture.debugElement.nativeElement.querySelector('#pagination');
        pagination.dispatchEvent(new Event('pageChange'));

        tick();
        expect(router.navigate.mock.calls[0][0]).toEqual(['/admin/system-notification-management']);
    }));
});
