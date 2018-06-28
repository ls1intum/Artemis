/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ModelingSubmissionComponent } from '../../../../../../main/webapp/app/entities/modeling-submission/modeling-submission.component';
import { ModelingSubmissionService } from '../../../../../../main/webapp/app/entities/modeling-submission/modeling-submission.service';
import { ModelingSubmission } from '../../../../../../main/webapp/app/entities/modeling-submission/modeling-submission.model';

describe('Component Tests', () => {

    describe('ModelingSubmission Management Component', () => {
        let comp: ModelingSubmissionComponent;
        let fixture: ComponentFixture<ModelingSubmissionComponent>;
        let service: ModelingSubmissionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ModelingSubmissionComponent],
                providers: [
                    ModelingSubmissionService
                ]
            })
            .overrideTemplate(ModelingSubmissionComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ModelingSubmissionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ModelingSubmissionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new ModelingSubmission(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.modelingSubmissions[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
