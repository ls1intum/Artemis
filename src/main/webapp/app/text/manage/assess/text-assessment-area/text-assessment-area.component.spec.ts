/**
 * Vitest tests for TextAssessmentAreaComponent.
 * Tests the text assessment area functionality with signal-based inputs.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TextAssessmentAreaComponent } from 'app/text/manage/assess/text-assessment-area/text-assessment-area.component';
import { TextBlockAssessmentCardComponent } from 'app/text/manage/assess/textblock-assessment-card/text-block-assessment-card.component';
import { TextBlockRef } from 'app/text/shared/entities/text-block-ref.model';
import { By } from '@angular/platform-browser';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';

describe('TextAssessmentAreaComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TextAssessmentAreaComponent;
    let fixture: ComponentFixture<TextAssessmentAreaComponent>;

    const submission = { id: 1, text: 'Test submission text' } as TextSubmission;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TextAssessmentAreaComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ id: 123 }) },
                MockProvider(StructuredGradingCriterionService),
                MockProvider(TextAssessmentAnalytics),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TextAssessmentAreaComponent);
        component = fixture.componentInstance;
        // Set required inputs
        fixture.componentRef.setInput('submission', submission);
        fixture.componentRef.setInput('textBlockRefs', []);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should add a TextblockAssessmentCardComponent for each TextBlockRef', () => {
        const textBlockRefs = [
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
        ];

        for (let i = 0; i < textBlockRefs.length; i++) {
            // Use setInput for model signals
            fixture.componentRef.setInput('textBlockRefs', textBlockRefs.slice(0, i));
            fixture.changeDetectorRef.detectChanges();

            const all = fixture.debugElement.queryAll(By.directive(TextBlockAssessmentCardComponent));
            expect(all).toHaveLength(i);
        }
    });

    it('should toggle on alt', () => {
        const spyOnAlt = vi.spyOn(component, 'onAltToggle');
        const eventMock = new KeyboardEvent('keydown', { key: 'Alt' });

        component.onAltToggle(eventMock, false);
        expect(spyOnAlt).toHaveBeenCalledOnce();
        expect(component.autoTextBlockAssessment).toBe(false);
    });

    it('should not toggle on alt when manual selection forbidden', () => {
        const spyOnAlt = vi.spyOn(component, 'onAltToggle');
        const eventMock = new KeyboardEvent('keydown', { key: 'Alt' });
        // Use setInput for input signals
        fixture.componentRef.setInput('allowManualBlockSelection', false);
        fixture.detectChanges();
        component.onAltToggle(eventMock, false);
        expect(spyOnAlt).toHaveBeenCalledOnce();
        expect(component.autoTextBlockAssessment).toBe(true);
    });

    it('should add TextBlockRef if text block is added manually', () => {
        const initialRefs = [TextBlockRef.new(), TextBlockRef.new(), TextBlockRef.new(), TextBlockRef.new()];
        fixture.componentRef.setInput('textBlockRefs', initialRefs);
        fixture.detectChanges();

        vi.spyOn(component.textBlockRefsAddedRemoved, 'emit');

        component.addTextBlockRef(TextBlockRef.new());
        fixture.changeDetectorRef.detectChanges();

        expect(component.textBlockRefsAddedRemoved.emit).toHaveBeenCalledOnce();
        expect(component.textBlockRefs()).toHaveLength(5);
    });

    it('should remove TextBlockRef if text block is deleted', () => {
        const initialRefs = [TextBlockRef.new(), TextBlockRef.new(), TextBlockRef.new(), TextBlockRef.new()];
        fixture.componentRef.setInput('textBlockRefs', initialRefs);
        fixture.detectChanges();

        vi.spyOn(component.textBlockRefsAddedRemoved, 'emit');

        component.removeTextBlockRef(component.textBlockRefs()[0]);
        fixture.changeDetectorRef.detectChanges();

        expect(component.textBlockRefsAddedRemoved.emit).toHaveBeenCalledOnce();
        expect(component.textBlockRefs()).toHaveLength(3);
    });
});
