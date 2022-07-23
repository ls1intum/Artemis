import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterEvent, RouterOutlet } from '@angular/router';
import { SystemNotificationManagementDetailComponent } from 'app/admin/system-notification-management/system-notification-management-detail.component';
import { SystemNotification } from 'app/entities/system-notification.model';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';

describe('SystemNotificationManagementDetailComponent', () => {
    let detailComponentFixture: ComponentFixture<SystemNotificationManagementDetailComponent>;
    let router: any;

    const route = {
        data: of({ notification: { id: 1, title: 'test' } as SystemNotification }),
        children: [],
    } as any as ActivatedRoute;

    beforeEach(() => {
        router = new MockRouter();
        router.setUrl('');

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                SystemNotificationManagementDetailComponent,
                MockRouterLinkDirective,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(RouterOutlet),
                MockComponent(FormDateTimePickerComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: router },
            ],
        })
            .compileComponents()
            .then(() => {
                detailComponentFixture = TestBed.createComponent(SystemNotificationManagementDetailComponent);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const dataSpy = jest.spyOn(route.data, 'subscribe');
        detailComponentFixture.detectChanges();
        expect(dataSpy).toHaveBeenCalledOnce();
    });

    it('should navigate to edit if edit is clicked', fakeAsync(() => {
        detailComponentFixture.detectChanges();

        const button = detailComponentFixture.debugElement.nativeElement.querySelector('#editButton');
        button.click();

        tick();
        expect(router.navigateByUrl).toHaveBeenCalledOnce();
        const navigationArray = router.navigateByUrl.mock.calls[0][0];
        expect(navigationArray).toEqual(['edit']);
    }));
});
