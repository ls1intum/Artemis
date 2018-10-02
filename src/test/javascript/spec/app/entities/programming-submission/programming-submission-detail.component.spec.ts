/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { ProgrammingSubmissionDetailComponent } from '../../../../../../main/webapp/app/entities/programming-submission/programming-submission-detail.component';
import { ProgrammingSubmissionService } from '../../../../../../main/webapp/app/entities/programming-submission/programming-submission.service';
import { ProgrammingSubmission } from '../../../../../../main/webapp/app/entities/programming-submission/programming-submission.model';

describe('Component Tests', () => {

    describe('ProgrammingSubmission Management Detail Component', () => {
        let comp: ProgrammingSubmissionDetailComponent;
        let fixture: ComponentFixture<ProgrammingSubmissionDetailComponent>;
        let service: ProgrammingSubmissionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [ProgrammingSubmissionDetailComponent],
                providers: [
                    ProgrammingSubmissionService
                ]
            })
            .overrideTemplate(ProgrammingSubmissionDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ProgrammingSubmissionDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ProgrammingSubmissionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new ProgrammingSubmission(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.programmingSubmission).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
