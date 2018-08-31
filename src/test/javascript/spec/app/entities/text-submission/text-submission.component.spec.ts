/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { TextSubmissionComponent } from '../../../../../../main/webapp/app/entities/text-submission/text-submission.component';
import { TextSubmissionService } from '../../../../../../main/webapp/app/entities/text-submission/text-submission.service';
import { TextSubmission } from '../../../../../../main/webapp/app/entities/text-submission/text-submission.model';

describe('Component Tests', () => {

    describe('TextSubmission Management Component', () => {
        let comp: TextSubmissionComponent;
        let fixture: ComponentFixture<TextSubmissionComponent>;
        let service: TextSubmissionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [TextSubmissionComponent],
                providers: [
                    TextSubmissionService
                ]
            })
            .overrideTemplate(TextSubmissionComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(TextSubmissionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(TextSubmissionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new TextSubmission(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.textSubmissions[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
