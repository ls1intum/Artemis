/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { PointCounterDetailComponent } from '../../../../../../main/webapp/app/entities/point-counter/point-counter-detail.component';
import { PointCounterService } from '../../../../../../main/webapp/app/entities/point-counter/point-counter.service';
import { PointCounter } from '../../../../../../main/webapp/app/entities/point-counter/point-counter.model';

describe('Component Tests', () => {

    describe('PointCounter Management Detail Component', () => {
        let comp: PointCounterDetailComponent;
        let fixture: ComponentFixture<PointCounterDetailComponent>;
        let service: PointCounterService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [PointCounterDetailComponent],
                providers: [
                    PointCounterService
                ]
            })
            .overrideTemplate(PointCounterDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(PointCounterDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(PointCounterService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new PointCounter(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.pointCounter).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
