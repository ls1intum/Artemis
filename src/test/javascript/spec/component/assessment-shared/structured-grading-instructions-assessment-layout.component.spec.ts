import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ExpandableSectionComponent } from 'app/assessment/assessment-instructions/expandable-section/expandable-section.component';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { LocalStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { ArtemisTestModule } from '../../test.module';

describe('StructuredGradingInstructionsAssessmentLayoutComponent', () => {
    let comp: StructuredGradingInstructionsAssessmentLayoutComponent;
    let fixture: ComponentFixture<StructuredGradingInstructionsAssessmentLayoutComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                StructuredGradingInstructionsAssessmentLayoutComponent,
                MockComponent(HelpIconComponent),
                ExpandableSectionComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockDirective(NgbTooltip),
                MockDirective(NgbCollapse),
            ],
            providers: [{ provide: LocalStorageService, useClass: MockLocalStorageService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StructuredGradingInstructionsAssessmentLayoutComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should initialize', () => {
        comp.readonly = true;
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
        comp.criteria = [gradingCriterionOne, gradingCriterionTwo];
        fixture.detectChanges();
        tick();
        expect(comp.expandableSections).toHaveLength(2);
        comp.expandableSections.forEach((section) => {
            expect(section.isCollapsed).toBeFalse();
        });
        comp.collapseAll();
        comp.expandableSections.forEach((section) => {
            expect(section.isCollapsed).toBeTrue();
        });
        comp.expandAll();
        comp.expandableSections.forEach((section) => {
            expect(section.isCollapsed).toBeFalse();
        });
    }));
});
