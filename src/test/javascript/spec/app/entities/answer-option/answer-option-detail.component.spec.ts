/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { AnswerOptionDetailComponent } from 'app/entities/answer-option/answer-option-detail.component';
import { AnswerOption } from 'app/shared/model/answer-option.model';

describe('Component Tests', () => {
    describe('AnswerOption Management Detail Component', () => {
        let comp: AnswerOptionDetailComponent;
        let fixture: ComponentFixture<AnswerOptionDetailComponent>;
        const route = ({ data: of({ answerOption: new AnswerOption(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [AnswerOptionDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(AnswerOptionDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(AnswerOptionDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.answerOption).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
