/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { SubmissionDetailComponent } from 'app/entities/submission/submission-detail.component';
import { Submission } from 'app/shared/model/submission.model';

describe('Component Tests', () => {
    describe('Submission Management Detail Component', () => {
        let comp: SubmissionDetailComponent;
        let fixture: ComponentFixture<SubmissionDetailComponent>;
        const route = ({ data: of({ submission: new Submission(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [SubmissionDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(SubmissionDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(SubmissionDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.submission).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
