/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { ResultComponent } from 'app/entities/result/result.component';
import { ResultService } from 'app/entities/result/result.service';
import { Result } from 'app/shared/model/result.model';

describe('Component Tests', () => {
    describe('Result Management Component', () => {
        let comp: ResultComponent;
        let fixture: ComponentFixture<ResultComponent>;
        let service: ResultService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [ResultComponent],
                providers: []
            })
                .overrideTemplate(ResultComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ResultComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ResultService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new Result(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.results[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
