/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { TextSubmissionDetailComponent } from '../../../../../../main/webapp/app/entities/text-submission/text-submission-detail.component';
import { TextSubmissionService } from '../../../../../../main/webapp/app/entities/text-submission/text-submission.service';
import { TextSubmission } from '../../../../../../main/webapp/app/entities/text-submission/text-submission.model';

describe('Component Tests', () => {

    describe('TextSubmission Management Detail Component', () => {
        let comp: TextSubmissionDetailComponent;
        let fixture: ComponentFixture<TextSubmissionDetailComponent>;
        let service: TextSubmissionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [TextSubmissionDetailComponent],
                providers: [
                    TextSubmissionService
                ]
            })
            .overrideTemplate(TextSubmissionDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(TextSubmissionDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(TextSubmissionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new TextSubmission(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.textSubmission).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
