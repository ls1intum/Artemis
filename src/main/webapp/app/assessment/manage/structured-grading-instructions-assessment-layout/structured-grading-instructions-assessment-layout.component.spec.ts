import { beforeEach, describe, expect, it, vi } from 'vitest';
import { computed, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { GradingInstructionSelectionHost, GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { TumUiCheckboxComponent } from '@tumaet/ui-angular';
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
import { deepClone } from 'app/foundation/util/deep-clone.util';

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

    it('should format the point pill of an instruction', () => {
        expect(comp.pointsLabel(1)).toBe('+1');
        expect(comp.pointsSeverity(1)).toBe('success');
        expect(comp.pointsLabel(0)).toBe('0');
        expect(comp.pointsSeverity(0)).toBe('secondary');
        expect(comp.pointsLabel(-1)).toBe('-1');
        expect(comp.pointsSeverity(-1)).toBe('danger');
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
        expect(comp.sortedCriteria()[1].instructions.map(({ instruction }) => instruction.instructionDescription)).toEqual([
            'All methods have proper JavaDoc.',
            'Not all methods have proper JavaDoc.',
        ]);
    });

    describe('with an editable feedback list registered', () => {
        const instruction = { id: 7, instructionDescription: 'description', feedback: 'feedback', credits: 4 } as GradingInstruction;
        const criterion = { id: 1, title: 'Documentation', structuredGradingInstructions: [instruction] } as GradingCriterion;
        let host: GradingInstructionSelectionHost;
        let appliedIds: ReturnType<typeof signal<ReadonlySet<number>>>;
        let appliedCounts: ReturnType<typeof signal<ReadonlyMap<number, number>>>;
        /** Set by the tests in which the instruction is applied to a referenced element the feedback list does not own. */
        let notRemovableIds: ReturnType<typeof signal<ReadonlySet<number>>>;

        beforeEach(() => {
            appliedIds = signal<ReadonlySet<number>>(new Set());
            appliedCounts = signal<ReadonlyMap<number, number>>(new Map());
            notRemovableIds = signal<ReadonlySet<number>>(new Set());
            // Tests mutate usageCount; reset so they cannot leak into each other.
            delete instruction.usageCount;
            host = {
                appliedInstructionIds: appliedIds,
                appliedInstructionCounts: appliedCounts,
                removableInstructionIds: computed(() => new Set([...appliedIds()].filter((id) => !notRemovableIds().has(id)))),
                applyInstruction: vi.fn(),
                unapplyOneInstruction: vi.fn(),
                unapplyInstruction: vi.fn(),
            };
            TestBed.inject(GradingInstructionSelectionService).register(host);
            fixture.componentRef.setInput('readonly', false);
            fixture.componentRef.setInput('criteria', [criterion]);
            comp.ngOnInit();
            fixture.detectChanges();
        });

        /** Marks the instruction as applied the given number of times (and therefore as present in appliedIds). */
        function setApplicationCount(count: number): void {
            if (count <= 0) {
                appliedIds.set(new Set());
                appliedCounts.set(new Map());
            } else {
                appliedIds.set(new Set([instruction.id!]));
                appliedCounts.set(new Map([[instruction.id!, count]]));
            }
            fixture.detectChanges();
        }

        /** The kit checkbox renders a real, visually hidden input that covers it, so a click always lands there. */
        function checkboxInput(): HTMLInputElement {
            return fixture.debugElement.query(By.directive(TumUiCheckboxComponent)).query(By.css('input[type="checkbox"]')).nativeElement;
        }

        function clickCheckbox(): void {
            checkboxInput().click();
            fixture.detectChanges();
        }

        it('should render a usage stepper for multi-use instructions and hide it for single-use ones', () => {
            // Default: unset usageCount means unlimited multi-use.
            expect(fixture.debugElement.query(By.directive(TumUiCheckboxComponent))).not.toBeNull();
            expect(fixture.nativeElement.querySelectorAll('[role="group"] button')).toHaveLength(2);
            expect(fixture.nativeElement.querySelector('[role="group"] span')?.textContent?.trim()).toBe('0 / ∞');
            expect(fixture.debugElement.query(By.css('jhi-help-icon'))).toBeNull();

            instruction.usageCount = 1;
            const clonedCriterion = deepClone(criterion);
            clonedCriterion.structuredGradingInstructions = [instruction];
            fixture.componentRef.setInput('criteria', [clonedCriterion]);
            fixture.detectChanges();

            expect(comp.showUsageStepper(instruction)).toBe(false);
            expect(fixture.nativeElement.querySelector('[role="group"]')).toBeNull();
        });

        it('should keep single-use instructions checkbox-only even when applied', () => {
            instruction.usageCount = 1;
            setApplicationCount(1);

            expect(comp.showUsageStepper(instruction)).toBe(false);
            expect(fixture.nativeElement.querySelector('[role="group"]')).toBeNull();
            expect(checkboxInput().checked).toBe(true);
        });

        it('should update the usage counter from linked feedback', () => {
            instruction.usageCount = 2;
            setApplicationCount(2);

            expect(comp.instructionUseCount(instruction)).toBe(2);
            expect(fixture.nativeElement.querySelector('[role="group"] span')?.textContent?.trim()).toBe('2 / 2');
        });

        it('should count the applied instructions of the criterion', () => {
            expect(comp.appliedCountPerCriterion()).toEqual([0]);

            appliedIds.set(new Set([instruction.id!]));
            expect(comp.appliedCountPerCriterion()).toEqual([1]);
            expect(comp.isApplied(instruction)).toBe(true);
        });

        it('should apply the instruction immediately when the checkbox is ticked', () => {
            clickCheckbox();

            expect(host.applyInstruction).toHaveBeenCalledWith(instruction);
            // The box follows the applied instructions rather than ticking itself.
            appliedIds.set(new Set([instruction.id!]));
            fixture.detectChanges();
            expect(checkboxInput().checked).toBe(true);
        });

        it('should ask for confirmation before un-applying, and not un-apply until confirmed', () => {
            const openDeleteDialogSpy = vi.spyOn(TestBed.inject(DeleteDialogService), 'openDeleteDialog').mockImplementation(() => {});
            appliedIds.set(new Set([instruction.id!]));
            fixture.detectChanges();

            clickCheckbox();

            // Unticking must not remove the feedback before the tutor confirms — same as the trash icon.
            expect(host.unapplyInstruction).not.toHaveBeenCalled();
            expect(openDeleteDialogSpy).toHaveBeenCalledOnce();
            const dialogData: DeleteDialogData = openDeleteDialogSpy.mock.calls[0][0];
            expect(dialogData.deleteQuestion).toBe('artemisApp.feedback.delete.allInstances');

            // Simulate the tutor confirming in the dialog.
            triggerDeleteDialogDelete(dialogData.delete, {});
            expect(host.unapplyInstruction).toHaveBeenCalledWith(instruction);
        });

        it('should keep the checkbox ticked while the confirmation is open and when the tutor cancels', () => {
            const openDeleteDialogSpy = vi.spyOn(TestBed.inject(DeleteDialogService), 'openDeleteDialog').mockImplementation(() => {});
            appliedIds.set(new Set([instruction.id!]));
            fixture.detectChanges();
            expect(checkboxInput().checked).toBe(true);

            // Cancelling means the dialog closes without ever invoking its `delete` callback.
            clickCheckbox();

            expect(host.unapplyInstruction).not.toHaveBeenCalled();
            expect(checkboxInput().checked).toBe(true);
            expect(fixture.debugElement.query(By.css('.tum-ui-checkbox-icon'))).not.toBeNull();

            // Clicking again must ask to un-apply once more instead of applying a duplicate feedback.
            clickCheckbox();

            expect(host.applyInstruction).not.toHaveBeenCalled();
            expect(openDeleteDialogSpy).toHaveBeenCalledTimes(2);
        });

        it('should untick the checkbox once the un-apply is confirmed', () => {
            const openDeleteDialogSpy = vi.spyOn(TestBed.inject(DeleteDialogService), 'openDeleteDialog').mockImplementation(() => {});
            appliedIds.set(new Set([instruction.id!]));
            fixture.detectChanges();

            clickCheckbox();
            triggerDeleteDialogDelete(openDeleteDialogSpy.mock.calls[0][0].delete, {});
            appliedIds.set(new Set());
            fixture.detectChanges();

            expect(checkboxInput().checked).toBe(false);
            expect(fixture.debugElement.query(By.css('.tum-ui-checkbox-icon'))).toBeNull();
        });

        /** The draggable attribute of the row that carries the instruction. */
        function instructionRowDraggable(): string | null {
            return fixture.debugElement.query(By.css('#criterion-0-instruction-0')).nativeElement.getAttribute('draggable');
        }

        it('should keep drag enabled after an application when the usage limit is unlimited', () => {
            // No usageCount / usageCount 0 means unlimited — ticking must not lock drag onto further targets.
            expect(comp.isDraggable(instruction)).toBe(true);
            expect(instructionRowDraggable()).toBe('true');

            setApplicationCount(1);

            expect(comp.isDraggable(instruction)).toBe(true);
            expect(instructionRowDraggable()).toBe('true');
        });

        it('should keep drag enabled until a finite usage limit is reached', () => {
            instruction.usageCount = 2;
            setApplicationCount(1);

            expect(comp.isDraggable(instruction)).toBe(true);
            expect(instructionRowDraggable()).toBe('true');

            setApplicationCount(2);

            expect(comp.isDraggable(instruction)).toBe(false);
            expect(instructionRowDraggable()).toBe('false');
        });

        it('should not hand over any instruction data once its usage limit is exhausted', () => {
            instruction.usageCount = 1;
            setApplicationCount(1);
            const dataTransfer = { setData: vi.fn() };
            const dragEvent = { dataTransfer, preventDefault: vi.fn() } as unknown as DragEvent;

            comp.drag(dragEvent, instruction);

            expect(dataTransfer.setData).not.toHaveBeenCalled();
            expect(dragEvent.preventDefault).toHaveBeenCalledOnce();
        });

        it('should show an instruction applied to a referenced element as ticked but locked', () => {
            const openDeleteDialogSpy = vi.spyOn(TestBed.inject(DeleteDialogService), 'openDeleteDialog').mockImplementation(() => {});
            setApplicationCount(1);
            notRemovableIds.set(new Set([instruction.id!]));
            fixture.detectChanges();

            expect(comp.isLockedByReferencedFeedback(instruction)).toBe(true);
            expect(checkboxInput().checked).toBe(true);
            expect(checkboxInput().disabled).toBe(true);

            // Even a click that reaches the host element must not offer to delete feedback this list does not own.
            comp.toggleApplied(new Event('click'), instruction);

            expect(openDeleteDialogSpy).not.toHaveBeenCalled();
            expect(host.unapplyInstruction).not.toHaveBeenCalled();
            expect(host.applyInstruction).not.toHaveBeenCalled();
        });

        it('should increment and decrement the application count from the usage stepper', () => {
            instruction.usageCount = 2;
            setApplicationCount(1);

            const [decrementButton, incrementButton] = fixture.nativeElement.querySelectorAll('[role="group"] button') as HTMLButtonElement[];
            expect(decrementButton.disabled).toBe(false);
            expect(incrementButton.disabled).toBe(false);

            incrementButton.click();
            expect(host.applyInstruction).toHaveBeenCalledWith(instruction);

            decrementButton.click();
            expect(host.unapplyOneInstruction).toHaveBeenCalledWith(instruction);
        });

        it('should disable the usage stepper at the empty and exhausted bounds', () => {
            instruction.usageCount = 2;

            expect(comp.canDecrementApplication(instruction)).toBe(false);
            expect(comp.canIncrementApplication(instruction)).toBe(true);

            setApplicationCount(2);
            expect(comp.canDecrementApplication(instruction)).toBe(true);
            expect(comp.canIncrementApplication(instruction)).toBe(false);

            notRemovableIds.set(new Set([instruction.id!]));
            fixture.detectChanges();
            expect(comp.canDecrementApplication(instruction)).toBe(false);
        });
    });

    it('should arm the instruction on Enter/Space when no editable feedback list is mounted', () => {
        fixture.componentRef.setInput('readonly', false);
        fixture.componentRef.setInput('criteria', undefined);
        fixture.detectChanges();
        const instruction = { id: 1, instructionDescription: 'description', credits: 4, usageCount: 0 } as GradingInstruction;
        const selectionService = TestBed.inject(GradingInstructionSelectionService);
        const armSpy = vi.spyOn(selectionService, 'armInstruction');

        expect(comp.selectable()).toBe(false);
        comp.onInstructionKeydown({ key: 'Enter', preventDefault: vi.fn() } as unknown as KeyboardEvent, instruction);

        expect(armSpy).toHaveBeenCalledExactlyOnceWith(instruction);
    });

    it('should not make selectable instruction cards keyboard-activatable', () => {
        fixture.componentRef.setInput('readonly', false);
        fixture.componentRef.setInput('criteria', [
            {
                id: 1,
                title: 'Documentation',
                structuredGradingInstructions: [{ id: 1, instructionDescription: 'description', credits: 4, usageCount: 0 } as GradingInstruction],
            } as GradingCriterion,
        ]);
        comp.ngOnInit();
        fixture.detectChanges();

        const host: GradingInstructionSelectionHost = {
            appliedInstructionIds: signal(new Set()),
            appliedInstructionCounts: signal(new Map()),
            removableInstructionIds: signal(new Set()),
            applyInstruction: vi.fn(),
            unapplyOneInstruction: vi.fn(),
            unapplyInstruction: vi.fn(),
        };
        const selectionService = TestBed.inject(GradingInstructionSelectionService);
        selectionService.register(host);
        fixture.detectChanges();

        const card = fixture.debugElement.query(By.css('#criterion-0-instruction-0')).nativeElement as HTMLElement;
        expect(comp.selectable()).toBe(true);
        expect(card.getAttribute('tabindex')).toBeNull();
        expect(card.getAttribute('role')).toBeNull();
        expect(fixture.debugElement.query(By.directive(TumUiCheckboxComponent))).not.toBeNull();

        const armSpy = vi.spyOn(selectionService, 'armInstruction');
        comp.onInstructionKeydown(
            { key: 'Enter', preventDefault: vi.fn() } as unknown as KeyboardEvent,
            {
                id: 1,
                instructionDescription: 'description',
                credits: 4,
                usageCount: 0,
            } as GradingInstruction,
        );

        expect(armSpy).not.toHaveBeenCalled();
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
        expect(fixture.debugElement.query(By.css('#criterion-0-instruction-0')).nativeElement.getAttribute('aria-labelledby')).toBe('criterion-0-instruction-0-desc');
        expect(fixture.debugElement.query(By.css('#criterion-0-instruction-0-desc'))).not.toBeNull();
    });

    it('should clear an armed instruction when the layout is destroyed', () => {
        const selectionService = TestBed.inject(GradingInstructionSelectionService);
        selectionService.armInstruction({ id: 1, credits: 1 } as GradingInstruction);
        expect(selectionService.hasArmedInstruction()).toBe(true);

        fixture.destroy();

        expect(selectionService.hasArmedInstruction()).toBe(false);
    });
});
