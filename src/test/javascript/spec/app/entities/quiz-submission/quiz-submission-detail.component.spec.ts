/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../../test.module';
import { QuizSubmissionDetailComponent } from 'app/exercises/quiz/participate/quiz-submission/quiz-submission-detail.component';
import { QuizSubmission } from 'app/shared/model/quiz-submission.model';

describe('Component Tests', () => {
    describe('QuizSubmission Management Detail Component', () => {
        let comp: QuizSubmissionDetailComponent;
        let fixture: ComponentFixture<QuizSubmissionDetailComponent>;
        const route = ({ data: of({ quizSubmission: new QuizSubmission(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [QuizSubmissionDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }],
            })
                .overrideTemplate(QuizSubmissionDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(QuizSubmissionDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.quizSubmission).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
