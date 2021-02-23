import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AngularFittextModule } from 'angular-fittext';
import { DragItemComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-item.component';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import * as chai from 'chai';
import { MockComponent, MockProvider } from 'ng-mocks';
import { DndModule } from 'ng2-dnd';
import { DeviceDetectorService, DeviceInfo } from 'ngx-device-detector';
import * as sinon from 'sinon';
import { stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('DragItemComponent', () => {
    let fixture: ComponentFixture<DragItemComponent>;
    let comp: DragItemComponent;
    let deviceDetectorService: DeviceDetectorService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, DndModule.forRoot(), AngularFittextModule],
            declarations: [MockComponent(SecuredImageComponent), DragItemComponent],
            providers: [MockProvider(DeviceDetectorService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DragItemComponent);
                comp = fixture.componentInstance;
                deviceDetectorService = fixture.debugElement.injector.get(DeviceDetectorService);
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).to.be.ok;
    });

    it('should get deviceInfo and isMobile from device service on init', () => {
        const deviceInfo = {
            userAgent: 'userAgent',
            os: 'os',
            browser: 'browser',
            device: 'device',
            os_version: 'os_version',
            browser_version: 'browser_version',
            deviceType: 'deviceType',
            orientation: 'orientation',
        } as DeviceInfo;
        const deviceInfoStub = stub(deviceDetectorService, 'getDeviceInfo').returns(deviceInfo);
        const isMobileStub = stub(deviceDetectorService, 'isMobile').returns(true);
        comp.ngOnInit();
        expect(deviceInfoStub).to.have.been.called;
        expect(isMobileStub).to.have.been.called;
        expect(comp.deviceInfo).to.deep.equal(deviceInfo);
        expect(comp.isMobile).to.equal(true);
    });
});
