/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { SubmittedAnswerComponent } from 'app/entities/submitted-answer/submitted-answer.component';
import { SubmittedAnswerService } from 'app/entities/submitted-answer/submitted-answer.service';
import { SubmittedAnswer } from 'app/shared/model/submitted-answer.model';

describe('Component Tests', () => {
    describe('SubmittedAnswer Management Component', () => {
        let comp: SubmittedAnswerComponent;
        let fixture: ComponentFixture<SubmittedAnswerComponent>;
        let service: SubmittedAnswerService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [SubmittedAnswerComponent],
                providers: []
            })
                .overrideTemplate(SubmittedAnswerComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(SubmittedAnswerComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(SubmittedAnswerService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new SubmittedAnswer(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.submittedAnswers[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
