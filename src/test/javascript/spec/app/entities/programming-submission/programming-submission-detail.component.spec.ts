/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ProgrammingSubmissionDetailComponent } from 'app/entities/programming-submission/programming-submission-detail.component';
import { ProgrammingSubmission } from 'app/shared/model/programming-submission.model';

describe('Component Tests', () => {
    describe('ProgrammingSubmission Management Detail Component', () => {
        let comp: ProgrammingSubmissionDetailComponent;
        let fixture: ComponentFixture<ProgrammingSubmissionDetailComponent>;
        const route = ({ data: of({ programmingSubmission: new ProgrammingSubmission(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ProgrammingSubmissionDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ProgrammingSubmissionDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ProgrammingSubmissionDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.programmingSubmission).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
