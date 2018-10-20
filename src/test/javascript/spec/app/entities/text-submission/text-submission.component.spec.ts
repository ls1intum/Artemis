/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { TextSubmissionComponent } from 'app/entities/text-submission/text-submission.component';
import { TextSubmissionService } from 'app/entities/text-submission/text-submission.service';
import { TextSubmission } from 'app/shared/model/text-submission.model';

describe('Component Tests', () => {
    describe('TextSubmission Management Component', () => {
        let comp: TextSubmissionComponent;
        let fixture: ComponentFixture<TextSubmissionComponent>;
        let service: TextSubmissionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [TextSubmissionComponent],
                providers: []
            })
                .overrideTemplate(TextSubmissionComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(TextSubmissionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(TextSubmissionService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new TextSubmission(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.textSubmissions[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
