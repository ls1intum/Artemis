/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ResultComponent } from '../../../../../../main/webapp/app/entities/result/result.component';
import { ResultService } from '../../../../../../main/webapp/app/entities/result/result.service';
import { Result } from '../../../../../../main/webapp/app/entities/result/result.model';

describe('Component Tests', () => {

    describe('Result Management Component', () => {
        let comp: ResultComponent;
        let fixture: ComponentFixture<ResultComponent>;
        let service: ResultService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ResultComponent],
                providers: [
                    ResultService
                ]
            })
            .overrideTemplate(ResultComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ResultComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ResultService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new Result(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.results[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
