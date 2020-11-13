import { ComponentFixture, TestBed, inject, tick, fakeAsync, async } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { mockedActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { Result } from 'app/entities/result.model';
import * as chai from 'chai';

describe('ModelingAssessmentComponent', () => {
    let comp: ModelingAssessmentComponent;
    let fixture: ComponentFixture<ModelingAssessmentComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule, TranslateModule.forRoot(), ArtemisTestModule, ModelingAssessmentModule],
            declarations: [],
            providers: [JhiLanguageHelper, mockedActivatedRoute({}, { showBackButton: 'false' })],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ModelingAssessmentComponent);
        comp = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        comp.ngOnDestroy();
    });

    it('should create', () => {
        expect(comp).toBeTruthy();
    });

    it('should set pageLoaded after view init', () => {
        comp.ngAfterViewInit();
        // expect(comp.pageLoaded).toBe(true);
    });

    it('update feedbacks after the view has been initialized', () => {
        comp.feedbacks.push({
            id: 0,
            text: 'fine',
            detailText: 'very fine',
            credits: 1,
            result: ({
                id: 2374,
                resultString: '1 of 12 points',
                score: 8,
                rated: true,
                hasFeedback: true,
                hasComplaint: false,
            } as unknown) as Result,
        });
        spyOn(comp.apollonEditor!, 'destroy').and.returnValue(true);
        comp.ngAfterViewInit();
        tick(500);
        expect(comp.apollonEditor!.destroy()).toHaveBeenCalled();
    });

    it('should destroy', () => {
        expect(comp.ngOnDestroy()).toBeTruthy();
    });
});
