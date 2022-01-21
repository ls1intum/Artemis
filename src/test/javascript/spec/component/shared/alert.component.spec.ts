import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTestModule } from '../../test.module';
import { AlertOverlayComponent } from 'app/shared/alert/alert-overlay.component';

describe('Alert Overlay Component Tests', () => {
    let comp: AlertOverlayComponent;
    let fixture: ComponentFixture<AlertOverlayComponent>;
    let alertService: AlertService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [AlertOverlayComponent],
        })
            .overrideTemplate(AlertOverlayComponent, '')
            .compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(AlertOverlayComponent);
        comp = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
    });

    it('Should call alertService.get on init', () => {
        const getStub = jest.spyOn(alertService, 'get');

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(getStub).toHaveBeenCalled();
    });

    it('Should call alertService.clear on destroy', () => {
        const clearStub = jest.spyOn(alertService, 'clear');

        // WHEN
        comp.ngOnDestroy();

        // THEN
        expect(clearStub).toHaveBeenCalled();
    });
});
