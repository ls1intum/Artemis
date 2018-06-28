/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { SubmissionComponent } from '../../../../../../main/webapp/app/entities/submission/submission.component';
import { SubmissionService } from '../../../../../../main/webapp/app/entities/submission/submission.service';
import { Submission } from '../../../../../../main/webapp/app/entities/submission/submission.model';

describe('Component Tests', () => {

    describe('Submission Management Component', () => {
        let comp: SubmissionComponent;
        let fixture: ComponentFixture<SubmissionComponent>;
        let service: SubmissionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [SubmissionComponent],
                providers: [
                    SubmissionService
                ]
            })
            .overrideTemplate(SubmissionComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(SubmissionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(SubmissionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new Submission(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.submissions[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
