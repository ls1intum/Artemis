import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DragItemComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-item.component';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { DeviceDetectorService, DeviceInfo } from 'ngx-device-detector';
import { ArtemisTestModule } from '../../test.module';
import { FitTextModule } from 'app/exercises/quiz/shared/fit-text/fit-text.module';

describe('DragItemComponent', () => {
    let fixture: ComponentFixture<DragItemComponent>;
    let comp: DragItemComponent;
    let deviceDetectorService: DeviceDetectorService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, DragDropModule, FitTextModule],
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

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
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
        const deviceInfoSpy = jest.spyOn(deviceDetectorService, 'getDeviceInfo').mockReturnValue(deviceInfo);
        const isMobileSpy = jest.spyOn(deviceDetectorService, 'isMobile').mockReturnValue(true);
        comp.ngOnInit();
        expect(deviceInfoSpy).toHaveBeenCalled();
        expect(isMobileSpy).toHaveBeenCalled();
        expect(comp.deviceInfo).toEqual(deviceInfo);
        expect(comp.isMobile).toBeTrue();
    });
});
