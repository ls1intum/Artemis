import { Directive, HostListener, Input } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterEvent, RouterOutlet } from '@angular/router';
import { SystemNotificationManagementDetailComponent } from 'app/admin/system-notification-management/system-notification-management-detail.component';
import { SystemNotification } from 'app/entities/system-notification.model';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';
import { MockRouter } from '../../../helpers/mocks/mock-router';


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

describe('SystemNotificationManagementDetailComponent', () => {
    let detailComponentFixture: ComponentFixture<SystemNotificationManagementDetailComponent>;
    let detailComponent: SystemNotificationManagementDetailComponent;
    let router: any;

    const route = {
        data: of({ notification: { id: 1, title: 'test' } as SystemNotification }),
        children: [],
    } as any as ActivatedRoute;

    beforeEach(() => {
        router = new MockRouter();
        router.events = of({ id: 1, url: '' } as RouterEvent);

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                SystemNotificationManagementDetailComponent,
                RouterLinkSpy,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(RouterOutlet),
                MockDirective(AlertErrorComponent),
                MockDirective(FormDateTimePickerComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: router },
            ],
        })
            .compileComponents()
            .then(() => {
                detailComponentFixture = TestBed.createComponent(SystemNotificationManagementDetailComponent);
                detailComponent = detailComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const dataSpy = jest.spyOn(route.data, 'subscribe');
        detailComponentFixture.detectChanges();
        expect(dataSpy).toHaveBeenCalledTimes(1);
    });

    it('should navigate to edit if edit is clicked', fakeAsync(() => {
        detailComponentFixture.detectChanges();

        const button = detailComponentFixture.debugElement.nativeElement.querySelector('#editButton');
        button.click();

        tick();
        expect(router.navigateByUrl).toHaveBeenCalledTimes(1);
        const navigationArray = router.navigateByUrl.mock.calls[0][0];
        expect(navigationArray).toEqual(['edit']);
    }));
});
