/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTeMiSTestModule } from '../../../test.module';
import { QuizSubmissionDetailComponent } from '../../../../../../main/webapp/app/entities/quiz-submission/quiz-submission-detail.component';
import { QuizSubmissionService } from '../../../../../../main/webapp/app/entities/quiz-submission/quiz-submission.service';
import { QuizSubmission } from '../../../../../../main/webapp/app/entities/quiz-submission/quiz-submission.model';

describe('Component Tests', () => {

    describe('QuizSubmission Management Detail Component', () => {
        let comp: QuizSubmissionDetailComponent;
        let fixture: ComponentFixture<QuizSubmissionDetailComponent>;
        let service: QuizSubmissionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [QuizSubmissionDetailComponent],
                providers: [
                    QuizSubmissionService
                ]
            })
            .overrideTemplate(QuizSubmissionDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(QuizSubmissionDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizSubmissionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new QuizSubmission(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.quizSubmission).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
