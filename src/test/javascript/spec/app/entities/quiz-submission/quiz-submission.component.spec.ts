/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { QuizSubmissionComponent } from '../../../../../../main/webapp/app/entities/quiz-submission/quiz-submission.component';
import { QuizSubmissionService } from '../../../../../../main/webapp/app/entities/quiz-submission/quiz-submission.service';
import { QuizSubmission } from '../../../../../../main/webapp/app/entities/quiz-submission/quiz-submission.model';

describe('Component Tests', () => {

    describe('QuizSubmission Management Component', () => {
        let comp: QuizSubmissionComponent;
        let fixture: ComponentFixture<QuizSubmissionComponent>;
        let service: QuizSubmissionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [QuizSubmissionComponent],
                providers: [
                    QuizSubmissionService
                ]
            })
            .overrideTemplate(QuizSubmissionComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(QuizSubmissionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizSubmissionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new QuizSubmission(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.quizSubmissions[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
