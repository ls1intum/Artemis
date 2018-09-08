/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ModelingSubmissionDetailComponent } from 'app/entities/modeling-submission/modeling-submission-detail.component';
import { ModelingSubmission } from 'app/shared/model/modeling-submission.model';

describe('Component Tests', () => {
    describe('ModelingSubmission Management Detail Component', () => {
        let comp: ModelingSubmissionDetailComponent;
        let fixture: ComponentFixture<ModelingSubmissionDetailComponent>;
        const route = ({ data: of({ modelingSubmission: new ModelingSubmission(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ModelingSubmissionDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ModelingSubmissionDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ModelingSubmissionDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.modelingSubmission).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
