/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTeMiSTestModule } from '../../../test.module';
import { ModelingSubmissionDetailComponent } from '../../../../../../main/webapp/app/entities/modeling-submission/modeling-submission-detail.component';
import { ModelingSubmissionService } from '../../../../../../main/webapp/app/entities/modeling-submission/modeling-submission.service';
import { ModelingSubmission } from '../../../../../../main/webapp/app/entities/modeling-submission/modeling-submission.model';

describe('Component Tests', () => {

    describe('ModelingSubmission Management Detail Component', () => {
        let comp: ModelingSubmissionDetailComponent;
        let fixture: ComponentFixture<ModelingSubmissionDetailComponent>;
        let service: ModelingSubmissionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ModelingSubmissionDetailComponent],
                providers: [
                    ModelingSubmissionService
                ]
            })
            .overrideTemplate(ModelingSubmissionDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ModelingSubmissionDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ModelingSubmissionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new ModelingSubmission(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.modelingSubmission).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
