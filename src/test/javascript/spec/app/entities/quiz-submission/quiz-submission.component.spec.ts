/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArtemisTestModule } from '../../../test.module';
import { QuizSubmissionComponent } from 'app/exercises/quiz/participate/quiz-submission/quiz-submission.component';
import { QuizParticipationService } from 'app/exercises/quiz/participate/quiz-participation.service';
import { QuizSubmission } from 'app/shared/model/quiz-submission.model';

describe('Component Tests', () => {
    describe('QuizSubmission Management Component', () => {
        let comp: QuizSubmissionComponent;
        let fixture: ComponentFixture<QuizSubmissionComponent>;
        let service: QuizParticipationService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [QuizSubmissionComponent],
                providers: [],
            })
                .overrideTemplate(QuizSubmissionComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(QuizSubmissionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizParticipationService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new QuizSubmission(123)],
                        headers,
                    }),
                ),
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.quizSubmissions[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
