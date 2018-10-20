/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ProgrammingSubmissionComponent } from 'app/entities/programming-submission/programming-submission.component';
import { ProgrammingSubmissionService } from 'app/entities/programming-submission/programming-submission.service';
import { ProgrammingSubmission } from 'app/shared/model/programming-submission.model';

describe('Component Tests', () => {
    describe('ProgrammingSubmission Management Component', () => {
        let comp: ProgrammingSubmissionComponent;
        let fixture: ComponentFixture<ProgrammingSubmissionComponent>;
        let service: ProgrammingSubmissionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ProgrammingSubmissionComponent],
                providers: []
            })
                .overrideTemplate(ProgrammingSubmissionComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ProgrammingSubmissionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ProgrammingSubmissionService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ProgrammingSubmission(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.programmingSubmissions[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
