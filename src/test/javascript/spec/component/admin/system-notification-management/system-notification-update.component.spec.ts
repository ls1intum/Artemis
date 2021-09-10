import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { SystemNotificationManagementUpdateComponent } from 'app/admin/system-notification-management/system-notification-management-update.component';
import { SystemNotification } from 'app/entities/system-notification.model';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import * as sinon from 'sinon';
import { spy } from 'sinon';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';

chai.use(sinonChai);
const expect = chai.expect;

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
            declarations: [
                SystemNotificationManagementUpdateComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(AlertErrorComponent),
                MockDirective(FormDateTimePickerComponent),
            ],
            providers: [{ provide: ActivatedRoute, useValue: route }, MockProvider(ArtemisNavigationUtilService)],
        })
            .compileComponents()
            .then(() => {
                updateComponentFixture = TestBed.createComponent(SystemNotificationManagementUpdateComponent);
                updateComponent = updateComponentFixture.componentInstance;
                service = TestBed.inject(SystemNotificationService);
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        updateComponentFixture.detectChanges();
        expect(updateComponent).to.be.ok;
    });

    it('navigate back if cancel is clicked', fakeAsync(() => {
        const previousStateSpy = spy(updateComponent, 'previousState');
        updateComponentFixture.detectChanges();

        const button = updateComponentFixture.debugElement.nativeElement.querySelector('#cancelButton');
        button.click();

        tick();
        expect(previousStateSpy).to.have.been.calledOnce;
    }));

    it('should update if save is clicked', fakeAsync(() => {
        const saveSpy = spy(updateComponent, 'save');
        spyOn(service, 'update').and.returnValue(of(new HttpResponse()));
        updateComponentFixture.detectChanges();

        const button = updateComponentFixture.debugElement.nativeElement.querySelector('#saveButton');
        button.click();

        tick();
        expect(saveSpy).to.have.been.calledOnce;
    }));

    it('should create if save is clicked', fakeAsync(() => {
        const saveSpy = spy(updateComponent, 'save');
        spyOn(service, 'create').and.returnValue(of(new HttpResponse()));
        updateComponentFixture.detectChanges();

        // Set to invalid id to emulate a new notification
        updateComponent.notification.id = undefined;
        updateComponentFixture.detectChanges();

        const button = updateComponentFixture.debugElement.nativeElement.querySelector('#saveButton');
        button.click();

        tick();
        expect(saveSpy).to.have.been.calledOnce;
    }));
});
