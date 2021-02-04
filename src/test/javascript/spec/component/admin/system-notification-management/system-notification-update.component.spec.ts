import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ActivatedRoute, Router } from '@angular/router';
import { ArtemisTestModule } from '../../../test.module';
import { TranslatePipe } from '@ngx-translate/core';
import { of } from 'rxjs';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { spy } from 'sinon';
import { SystemNotificationManagementUpdateComponent } from 'app/admin/system-notification-management/system-notification-management-update.component';
import { FormsModule } from '@angular/forms';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { SystemNotification } from 'app/entities/system-notification.model';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { HttpResponse } from '@angular/common/http';

chai.use(sinonChai);
const expect = chai.expect;

describe('SystemNotificationManagementUpdateComponent', () => {
    let updateComponentFixture: ComponentFixture<SystemNotificationManagementUpdateComponent>;
    let updateComponent: SystemNotificationManagementUpdateComponent;
    let service: SystemNotificationService;

    const route = ({
        parent: {
            data: of({ notification: { id: 1, title: 'test' } as SystemNotification }),
        },
    } as any) as ActivatedRoute;
    const router = { navigate() {} };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [SystemNotificationManagementUpdateComponent, MockPipe(TranslatePipe), MockDirective(AlertErrorComponent), MockDirective(FormDateTimePickerComponent)],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: router },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                updateComponentFixture = TestBed.createComponent(SystemNotificationManagementUpdateComponent);
                updateComponent = updateComponentFixture.componentInstance;
                service = updateComponentFixture.debugElement.injector.get(SystemNotificationService);
                sinon.stub(router, 'navigate');
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
