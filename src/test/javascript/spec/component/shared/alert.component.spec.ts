import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTestModule } from '../../test.module';
import { AlertComponent } from 'app/shared/alert/alert.component';

describe('Alert Component Tests', () => {
    let comp: AlertComponent;
    let fixture: ComponentFixture<AlertComponent>;
    let alertService: AlertService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [AlertComponent],
        })
            .overrideTemplate(AlertComponent, '')
            .compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(AlertComponent);
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
