import { beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { GradingInstructionSelectionHost, GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { TumUiCheckboxComponent } from 'app/shared-ui/tum-ui/checkbox/tum-ui-checkbox.component';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/manage/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { ExpandableSectionComponent } from 'app/assessment/manage/assessment-instructions/expandable-section/expandable-section.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { DeleteDialogData, triggerDeleteDialogDelete } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { provideHttpClient } from '@angular/common/http';

describe('StructuredGradingInstructionsAssessmentLayoutComponent', () => {
    let comp: StructuredGradingInstructionsAssessmentLayoutComponent;
    let fixture: ComponentFixture<StructuredGradingInstructionsAssessmentLayoutComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                MockDirective(NgbTooltip),
                MockDirective(NgbCollapse),
                FaIconComponent,
                StructuredGradingInstructionsAssessmentLayoutComponent,
                MockComponent(HelpIconComponent),
                ExpandableSectionComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(MarkdownDirective),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, { provide: DialogService, useClass: MockDialogService }, provideHttpClient()],
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
        expect(comp.allowDrop()).toBe(false);
        expect(comp.disableDrag()).toBe(false);
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

    it('should expand and collapse all criteria', () => {
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

        expect(comp.expandableSections()).toHaveLength(2);
        comp.expandableSections().forEach((section) => {
            expect(section.isCollapsed()).toBe(false);
        });
        comp.collapseAll();
        comp.expandableSections().forEach((section) => {
            expect(section.isCollapsed()).toBe(true);
        });
        comp.expandAll();
        comp.expandableSections().forEach((section) => {
            expect(section.isCollapsed()).toBe(false);
        });
    });

    it('should sort criteria and their instructions alphabetically', () => {
        const documentation = {
            id: 1,
            title: 'Documentation',
            structuredGradingInstructions: [
                { id: 1, instructionDescription: 'Not all methods have proper JavaDoc.', credits: 0 } as GradingInstruction,
                { id: 2, instructionDescription: 'All methods have proper JavaDoc.', credits: 4 } as GradingInstruction,
            ],
        } as GradingCriterion;
        const camera = {
            id: 2,
            title: 'Camera',
            structuredGradingInstructions: [{ id: 3, instructionDescription: 'The camera follows.', credits: 4 } as GradingInstruction],
        } as GradingCriterion;
        fixture.componentRef.setInput('criteria', [documentation, camera]);

        expect(comp.sortedCriteria().map((criterion) => criterion.title)).toEqual(['Camera', 'Documentation']);
        expect(comp.sortedCriteria()[1].instructions.map((instruction) => instruction.instructionDescription)).toEqual([
            'All methods have proper JavaDoc.',
            'Not all methods have proper JavaDoc.',
        ]);
    });

    describe('with an editable feedback list registered', () => {
        const instruction = { id: 7, instructionDescription: 'description', feedback: 'feedback', credits: 4 } as GradingInstruction;
        const criterion = { id: 1, title: 'Documentation', structuredGradingInstructions: [instruction] } as GradingCriterion;
        let host: GradingInstructionSelectionHost;
        let appliedIds: ReturnType<typeof signal<ReadonlySet<number>>>;

        beforeEach(() => {
            appliedIds = signal<ReadonlySet<number>>(new Set());
            host = {
                appliedInstructionIds: appliedIds,
                applyInstruction: vi.fn(),
                unapplyInstruction: vi.fn(),
            };
            TestBed.inject(GradingInstructionSelectionService).register(host);
            fixture.componentRef.setInput('readonly', false);
            fixture.componentRef.setInput('criteria', [criterion]);
            comp.ngOnInit();
            fixture.detectChanges();
        });

        it('should render a checkbox instead of the usage count', () => {
            expect(fixture.debugElement.query(By.directive(TumUiCheckboxComponent))).not.toBeNull();
            expect(fixture.debugElement.query(By.css('jhi-help-icon'))).toBeNull();
        });

        it('should count the applied instructions of the criterion', () => {
            expect(comp.appliedCountPerCriterion()).toEqual([0]);

            appliedIds.set(new Set([instruction.id!]));
            expect(comp.appliedCountPerCriterion()).toEqual([1]);
            expect(comp.isApplied(instruction)).toBe(true);
        });

        it('should apply the instruction immediately when the checkbox is checked', () => {
            comp.toggleApplied(instruction, true);
            expect(host.applyInstruction).toHaveBeenCalledWith(instruction);
        });

        it('should ask for confirmation before un-applying, and not un-apply until confirmed', () => {
            const openDeleteDialogSpy = vi.spyOn(TestBed.inject(DeleteDialogService), 'openDeleteDialog').mockImplementation(() => {});

            comp.toggleApplied(instruction, false);

            // Unticking must not remove the feedback before the tutor confirms — same as the trash icon.
            expect(host.unapplyInstruction).not.toHaveBeenCalled();
            expect(openDeleteDialogSpy).toHaveBeenCalledOnce();
            const dialogData: DeleteDialogData = openDeleteDialogSpy.mock.calls[0][0];
            expect(dialogData.deleteQuestion).toBe('artemisApp.feedback.delete.question');

            // Simulate the tutor confirming in the dialog.
            triggerDeleteDialogDelete(dialogData.delete, {});
            expect(host.unapplyInstruction).toHaveBeenCalledWith(instruction);
        });
    });

    it('should keep the usage count when no editable feedback list is mounted', () => {
        fixture.componentRef.setInput('readonly', false);
        fixture.componentRef.setInput('criteria', [
            {
                id: 1,
                title: 'Documentation',
                structuredGradingInstructions: [{ id: 1, instructionDescription: 'description', credits: 4, usageCount: 2 } as GradingInstruction],
            } as GradingCriterion,
        ]);
        comp.ngOnInit();
        fixture.detectChanges();

        expect(comp.selectable()).toBe(false);
        expect(fixture.debugElement.query(By.directive(TumUiCheckboxComponent))).toBeNull();
        expect(fixture.debugElement.query(By.css('jhi-help-icon'))).not.toBeNull();
    });
});
