import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { AlertOverlayComponent } from 'app/core/alert/alert-overlay.component';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Alert Overlay Component Tests', () => {
    let comp: AlertOverlayComponent;
    let fixture: ComponentFixture<AlertOverlayComponent>;
    let alertService: AlertService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: AlertService, useClass: AlertService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
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
        jest.useFakeTimers();

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
        jest.runOnlyPendingTimers();

        expect(onClose).toHaveBeenCalledWith(alert);
        expect(onClose).toHaveBeenCalledOnce();
        expect(alertService.get()).toHaveLength(0);

        jest.useRealTimers();
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
