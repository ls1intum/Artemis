import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/manage/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ExpandableSectionComponent } from 'app/assessment/manage/assessment-instructions/expandable-section/expandable-section.component';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('StructuredGradingInstructionsAssessmentLayoutComponent', () => {
    let comp: StructuredGradingInstructionsAssessmentLayoutComponent;
    let fixture: ComponentFixture<StructuredGradingInstructionsAssessmentLayoutComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip), MockDirective(NgbCollapse), FaIconComponent],
            declarations: [
                StructuredGradingInstructionsAssessmentLayoutComponent,
                MockComponent(HelpIconComponent),
                ExpandableSectionComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StructuredGradingInstructionsAssessmentLayoutComponent);
                comp = fixture.componentInstance;
                fixture.componentRef.setInput('readonly', undefined);
                fixture.componentRef.setInput('criteria', undefined);
            });
    });

    it('should initialize', () => {
        fixture.componentRef.setInput('readonly', true);

        comp.ngOnInit();
        expect(comp.allowDrop).toBeFalse();
        expect(comp.disableDrag()).toBeFalse();
    });

    it('should set display elements', () => {
        const gradingInstruction = { id: 1, feedback: 'feedback', credits: 1 } as GradingInstruction;

        expect(comp.setScore(gradingInstruction.credits)).toBe('1P');
        expect(comp.setTooltip(gradingInstruction)).toBe('Feedback: feedback');
        expect(comp.setInstrColour(gradingInstruction)).toBe('var(--sgi-assessment-layout-positive-background)');
        gradingInstruction.credits = 0;
        fixture.detectChanges();
        expect(comp.setInstrColour(gradingInstruction)).toBe('var(--sgi-assessment-layout-zero-background)');
        gradingInstruction.credits = -1;
        fixture.detectChanges();
        expect(comp.setInstrColour(gradingInstruction)).toBe('var(--sgi-assessment-layout-negative-background)');
    });

    it('should expand and collapse all criteria', fakeAsync(() => {
        const gradingCriterionOne = {
            id: 1,
            title: 'title',
            structuredGradingInstructions: [{ id: 1, feedback: 'feedback', credits: 1 } as GradingInstruction],
        } as GradingCriterion;
        const gradingCriterionTwo = {
            id: 2,
            title: 'title',
            structuredGradingInstructions: [{ id: 2, feedback: 'feedback', credits: 1 } as GradingInstruction],
        } as GradingCriterion;
        fixture.componentRef.setInput('criteria', [gradingCriterionOne, gradingCriterionTwo]);
        fixture.detectChanges();
        tick();
        expect(comp.expandableSections()).toHaveLength(2);
        comp.expandableSections().forEach((section) => {
            expect(section.isCollapsed).toBeFalse();
        });
        comp.collapseAll();
        comp.expandableSections().forEach((section) => {
            expect(section.isCollapsed).toBeTrue();
        });
        comp.expandAll();
        comp.expandableSections().forEach((section) => {
            expect(section.isCollapsed).toBeFalse();
        });
    }));
});
