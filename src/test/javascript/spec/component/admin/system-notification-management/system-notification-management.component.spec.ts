import { Directive, HostListener, Input } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, RouterEvent, RouterOutlet } from '@angular/router';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { SystemNotificationManagementComponent } from 'app/admin/system-notification-management/system-notification-management.component';
import { SystemNotification } from 'app/entities/system-notification.model';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import * as moment from 'moment';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import * as sinon from 'sinon';
import { stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { MockRouter } from '../../../helpers/mocks/service/mock-route.service';
import { ArtemisTestModule } from '../../../test.module';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';

chai.use(sinonChai);
const expect = chai.expect;

@Directive({
    // tslint:disable-next-line:directive-selector
    selector: '[routerLink]',
})
// tslint:disable-next-line:directive-class-suffix
class RouterLinkSpy {
    @Input()
    routerLink = '';

    constructor(private router: Router) {}

    @HostListener('click')
    onClick() {
        this.router.navigateByUrl(this.routerLink);
    }
}

describe('SystemNotificationManagementComponent', () => {
    let managementComponentFixture: ComponentFixture<SystemNotificationManagementComponent>;
    let managementComponent: SystemNotificationManagementComponent;
    let router: any;

    const route = {
        data: of({ pagingParams: {} }),
        children: [],
    } as any as ActivatedRoute;

    beforeEach(() => {
        router = new MockRouter();
        router.setEvents(of({ id: 1, url: '' } as RouterEvent));

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                SystemNotificationManagementComponent,
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
                RouterLinkSpy,
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
                router = managementComponentFixture.debugElement.injector.get(Router);
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        managementComponentFixture.detectChanges();
        expect(managementComponent).to.be.ok;
    });

    it('navigate to the details page of system notification if details is clicked', fakeAsync(() => {
        const notification = new SystemNotification();
        notification.id = 1;
        notification.expireDate = moment();
        notification.notificationDate = moment();
        managementComponent.notifications = [notification];
        managementComponentFixture.detectChanges();

        const button = managementComponentFixture.debugElement.nativeElement.querySelector('#viewButton');
        button.click();

        tick();
        expect(router.navigateByUrl).to.have.been.calledOnce;
        const navigationArray = router.navigateByUrl.getCall(0).args[0];
        expect(navigationArray).to.deep.equal(['./', notification.id]);
    }));

    it('navigate to the edit page of system notification if details is clicked', fakeAsync(() => {
        const notification = new SystemNotification();
        notification.id = 2;
        notification.expireDate = moment();
        notification.notificationDate = moment();
        managementComponent.notifications = [notification];
        managementComponentFixture.detectChanges();

        const button = managementComponentFixture.debugElement.nativeElement.querySelector('#editButton');
        button.click();

        tick();
        expect(router.navigateByUrl).to.have.been.calledOnce;
        const navigationArray = router.navigateByUrl.getCall(0).args[0];
        expect(navigationArray).to.deep.equal(['./', notification.id, 'edit']);
    }));

    it('should unsubscribe on destroy', () => {
        const routeDataSpy = stub(managementComponent.routeData, 'unsubscribe');
        managementComponentFixture.destroy();
        expect(routeDataSpy).to.have.been.calledOnce;
    });

    it('should transition on page change', fakeAsync(() => {
        managementComponent.notifications = [];
        managementComponentFixture.detectChanges();

        const pagination = managementComponentFixture.debugElement.nativeElement.querySelector('#pagination');
        pagination.dispatchEvent(new Event('pageChange'));

        tick();
        const navigationArray = router.navigate.getCall(0).args[0];
        expect(navigationArray).to.deep.equal(['/admin/system-notification-management']);
    }));
});
