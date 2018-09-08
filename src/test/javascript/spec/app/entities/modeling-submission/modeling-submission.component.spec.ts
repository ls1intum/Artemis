/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ModelingSubmissionComponent } from 'app/entities/modeling-submission/modeling-submission.component';
import { ModelingSubmissionService } from 'app/entities/modeling-submission/modeling-submission.service';
import { ModelingSubmission } from 'app/shared/model/modeling-submission.model';

describe('Component Tests', () => {
    describe('ModelingSubmission Management Component', () => {
        let comp: ModelingSubmissionComponent;
        let fixture: ComponentFixture<ModelingSubmissionComponent>;
        let service: ModelingSubmissionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ModelingSubmissionComponent],
                providers: []
            })
                .overrideTemplate(ModelingSubmissionComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ModelingSubmissionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ModelingSubmissionService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ModelingSubmission(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.modelingSubmissions[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
