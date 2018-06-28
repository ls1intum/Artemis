/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { PointCounterComponent } from '../../../../../../main/webapp/app/entities/point-counter/point-counter.component';
import { PointCounterService } from '../../../../../../main/webapp/app/entities/point-counter/point-counter.service';
import { PointCounter } from '../../../../../../main/webapp/app/entities/point-counter/point-counter.model';

describe('Component Tests', () => {

    describe('PointCounter Management Component', () => {
        let comp: PointCounterComponent;
        let fixture: ComponentFixture<PointCounterComponent>;
        let service: PointCounterService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [PointCounterComponent],
                providers: [
                    PointCounterService
                ]
            })
            .overrideTemplate(PointCounterComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(PointCounterComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(PointCounterService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new PointCounter(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.pointCounters[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
