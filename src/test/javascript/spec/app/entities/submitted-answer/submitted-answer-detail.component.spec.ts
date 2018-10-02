/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { SubmittedAnswerDetailComponent } from 'app/entities/submitted-answer/submitted-answer-detail.component';
import { SubmittedAnswer } from 'app/shared/model/submitted-answer.model';

describe('Component Tests', () => {
    describe('SubmittedAnswer Management Detail Component', () => {
        let comp: SubmittedAnswerDetailComponent;
        let fixture: ComponentFixture<SubmittedAnswerDetailComponent>;
        const route = ({ data: of({ submittedAnswer: new SubmittedAnswer(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [SubmittedAnswerDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(SubmittedAnswerDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(SubmittedAnswerDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.submittedAnswer).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
