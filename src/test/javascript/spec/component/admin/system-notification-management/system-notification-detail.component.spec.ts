import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { SystemNotificationManagementDetailComponent } from 'app/core/admin/system-notification-management/system-notification-management-detail.component';
import { SystemNotification } from 'app/entities/system-notification.model';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('SystemNotificationManagementDetailComponent', () => {
    let detailComponentFixture: ComponentFixture<SystemNotificationManagementDetailComponent>;
    let router: MockRouter;

    const route = {
        data: of({ notification: { id: 1, title: 'test' } as SystemNotification }),
        children: [],
    } as any as ActivatedRoute;

    beforeEach(() => {
        router = new MockRouter();
        router.setUrl('');

        TestBed.configureTestingModule({
            imports: [FormsModule, MockModule(RouterModule)],
            declarations: [MockRouterLinkDirective, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockComponent(FormDateTimePickerComponent)],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: router },
                { provide: TranslateService, useClass: MockTranslateService },
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
        const navigateByUrlSpy = jest.spyOn(router, 'navigateByUrl');
        detailComponentFixture.detectChanges();

        const button = detailComponentFixture.debugElement.nativeElement.querySelector('#editButton');
        button.click();

        tick();
        expect(navigateByUrlSpy).toHaveBeenCalledOnce();
    }));
});
