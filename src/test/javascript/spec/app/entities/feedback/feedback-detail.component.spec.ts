/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { FeedbackDetailComponent } from 'app/entities/feedback/feedback-detail.component';
import { Feedback } from 'app/shared/model/feedback.model';

describe('Component Tests', () => {
    describe('Feedback Management Detail Component', () => {
        let comp: FeedbackDetailComponent;
        let fixture: ComponentFixture<FeedbackDetailComponent>;
        const route = ({ data: of({ feedback: new Feedback(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [FeedbackDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(FeedbackDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(FeedbackDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.feedback).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
