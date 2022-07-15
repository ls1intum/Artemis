import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { SystemNotificationManagementUpdateComponent } from 'app/admin/system-notification-management/system-notification-management-update.component';
import { SystemNotification } from 'app/entities/system-notification.model';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';

describe('SystemNotificationManagementUpdateComponent', () => {
    let updateComponentFixture: ComponentFixture<SystemNotificationManagementUpdateComponent>;
    let updateComponent: SystemNotificationManagementUpdateComponent;
    let service: SystemNotificationService;

    const route = {
        parent: {
            data: of({ notification: { id: 1, title: 'test' } as SystemNotification }),
        },
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [SystemNotificationManagementUpdateComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FormDateTimePickerComponent)],
            providers: [{ provide: ActivatedRoute, useValue: route }, MockProvider(ArtemisNavigationUtilService)],
        })
            .compileComponents()
            .then(() => {
                updateComponentFixture = TestBed.createComponent(SystemNotificationManagementUpdateComponent);
                updateComponent = updateComponentFixture.componentInstance;
                service = TestBed.inject(SystemNotificationService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        updateComponentFixture.detectChanges();
        expect(updateComponent).not.toBeNull();
    });

    it('navigate back if cancel is clicked', fakeAsync(() => {
        const previousStateSpy = jest.spyOn(updateComponent, 'previousState');
        updateComponentFixture.detectChanges();

        const button = updateComponentFixture.debugElement.nativeElement.querySelector('#cancelButton');
        button.click();

        tick();
        expect(previousStateSpy).toHaveBeenCalledOnce();
    }));

    it('should update if save is clicked', fakeAsync(() => {
        const saveSpy = jest.spyOn(updateComponent, 'save');
        jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse<SystemNotification>()));
        updateComponentFixture.detectChanges();

        const button = updateComponentFixture.debugElement.nativeElement.querySelector('#saveButton');
        button.click();

        tick();
        expect(saveSpy).toHaveBeenCalledOnce();
    }));

    it('should create if save is clicked', fakeAsync(() => {
        const saveSpy = jest.spyOn(updateComponent, 'save');
        jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse<SystemNotification>()));
        updateComponentFixture.detectChanges();

        // Set to invalid id to emulate a new notification
        updateComponent.notification.id = undefined;
        updateComponentFixture.detectChanges();

        const button = updateComponentFixture.debugElement.nativeElement.querySelector('#saveButton');
        button.click();

        tick();
        expect(saveSpy).toHaveBeenCalledOnce();
    }));
});
