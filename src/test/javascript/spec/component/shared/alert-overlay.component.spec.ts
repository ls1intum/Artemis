import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { AlertOverlayComponent } from 'app/shared/alert/alert-overlay.component';
import { CloseCircleComponent } from 'app/shared/close-circle/close-circle.component';
import { ArtemisTestModule } from '../../test.module';

describe('Alert Overlay Component Tests', () => {
    let comp: AlertOverlayComponent;
    let fixture: ComponentFixture<AlertOverlayComponent>;
    let alertService: AlertService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NoopAnimationsModule],
            declarations: [AlertOverlayComponent, CloseCircleComponent],
            providers: [{ provide: AlertService, useClass: AlertService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AlertOverlayComponent);
                comp = fixture.componentInstance;
                alertService = TestBed.inject(AlertService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call alertService.get on init', () => {
        const getStub = jest.spyOn(alertService, 'get');

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(getStub).toHaveBeenCalledOnce();
    });

    it('should close all alerts on destroy', () => {
        const clearStub = jest.spyOn(alertService, 'closeAll');

        // WHEN
        comp.ngOnDestroy();

        // THEN
        expect(clearStub).toHaveBeenCalledOnce();
    });

    it('should call action callback if button is clicked', () => {
        comp.ngOnInit();

        const callback = jest.fn();
        const alert = alertService.addAlert({
            type: AlertType.INFO,
            message: '123',
            action: {
                label: 'button',
                callback,
            },
        });

        fixture.detectChanges();

        const btn = fixture.debugElement.query(By.css('.btn'));
        expect(btn).not.toBeNull();

        btn.nativeElement.click();

        expect(callback).toHaveBeenCalledWith(alert);
        expect(callback).toHaveBeenCalledOnce();
    });

    it('should close the alert if the close icon is clicked', () => {
        comp.ngOnInit();

        const onClose = jest.fn();
        const alert = alertService.addAlert({
            type: AlertType.INFO,
            message: '123',
            onClose,
        });
        expect(alertService.get()).toHaveLength(1);

        fixture.detectChanges();

        const btn = fixture.debugElement.query(By.css('jhi-close-circle'));
        expect(btn).not.toBeNull();

        btn.nativeElement.click();

        expect(onClose).toHaveBeenCalledWith(alert);
        expect(onClose).toHaveBeenCalledOnce();
        expect(alertService.get()).toHaveLength(0);
    });

    it('should not render the close icon if alert is not dismissible', () => {
        comp.ngOnInit();

        alertService.addAlert({
            type: AlertType.INFO,
            message: '123',
            dismissible: false,
        });

        const btn = fixture.debugElement.query(By.css('jhi-close-circle'));
        expect(btn).toBeNull();
    });
});
