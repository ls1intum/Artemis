/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ProgrammingSubmissionComponent } from '../../../../../../main/webapp/app/entities/programming-submission/programming-submission.component';
import { ProgrammingSubmissionService } from '../../../../../../main/webapp/app/entities/programming-submission/programming-submission.service';
import { ProgrammingSubmission } from '../../../../../../main/webapp/app/entities/programming-submission/programming-submission.model';

describe('Component Tests', () => {

    describe('ProgrammingSubmission Management Component', () => {
        let comp: ProgrammingSubmissionComponent;
        let fixture: ComponentFixture<ProgrammingSubmissionComponent>;
        let service: ProgrammingSubmissionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ProgrammingSubmissionComponent],
                providers: [
                    ProgrammingSubmissionService
                ]
            })
            .overrideTemplate(ProgrammingSubmissionComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ProgrammingSubmissionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ProgrammingSubmissionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new ProgrammingSubmission(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.programmingSubmissions[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
