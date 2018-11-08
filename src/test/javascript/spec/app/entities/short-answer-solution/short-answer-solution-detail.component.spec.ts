/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSolutionDetailComponent } from 'app/entities/short-answer-solution/short-answer-solution-detail.component';
import { ShortAnswerSolution } from 'app/entities/short-answer-solution/short-answer-solution.model';

describe('Component Tests', () => {
    describe('ShortAnswerSolution Management Detail Component', () => {
        let comp: ShortAnswerSolutionDetailComponent;
        let fixture: ComponentFixture<ShortAnswerSolutionDetailComponent>;
        const route = ({ data: of({ shortAnswerSolution: new ShortAnswerSolution(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSolutionDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ShortAnswerSolutionDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ShortAnswerSolutionDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.shortAnswerSolution).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
