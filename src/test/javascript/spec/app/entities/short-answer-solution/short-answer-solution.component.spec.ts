/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSolutionComponent } from 'app/entities/short-answer-solution/short-answer-solution.component';
import { ShortAnswerSolutionService } from 'app/entities/short-answer-solution/short-answer-solution.service';
import { ShortAnswerSolution } from 'app/entities/short-answer-solution/short-answer-solution.model';

describe('Component Tests', () => {
    describe('ShortAnswerSolution Management Component', () => {
        let comp: ShortAnswerSolutionComponent;
        let fixture: ComponentFixture<ShortAnswerSolutionComponent>;
        let service: ShortAnswerSolutionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSolutionComponent],
                providers: []
            })
                .overrideTemplate(ShortAnswerSolutionComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerSolutionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerSolutionService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ShortAnswerSolution(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.shortAnswerSolutions[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
