/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { TextSubmissionDetailComponent } from 'app/entities/text-submission/text-submission-detail.component';
import { TextSubmission } from 'app/shared/model/text-submission.model';

describe('Component Tests', () => {
    describe('TextSubmission Management Detail Component', () => {
        let comp: TextSubmissionDetailComponent;
        let fixture: ComponentFixture<TextSubmissionDetailComponent>;
        const route = ({ data: of({ textSubmission: new TextSubmission(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [TextSubmissionDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(TextSubmissionDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(TextSubmissionDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.textSubmission).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
