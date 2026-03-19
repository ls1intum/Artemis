import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { BuildPhaseEditorComponent } from './build-phase-editor.component';
import { BUILD_PHASE_CONDITION, BuildPhase } from 'app/programming/shared/entities/build-plan-phases.model';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { MonacoEditorFitTextComponent } from '../monaco-editor-auto-size/monaco-editor-fit-text.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('BuildPhaseEditorComponent', () => {
    setupTestBed({ zoneless: true });

    let component: BuildPhaseEditorComponent;
    let fixture: ComponentFixture<BuildPhaseEditorComponent>;

    const initialPhase: BuildPhase = {
        name: 'build',
        script: 'echo "building..."',
        condition: 'ALWAYS',
        forceRun: false,
        resultPaths: ['**/results.xml', '**/coverage.xml'],
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildPhaseEditorComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(BuildPhaseEditorComponent, {
                remove: { imports: [MonacoEditorFitTextComponent, TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockComponent(MonacoEditorFitTextComponent), MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(BuildPhaseEditorComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('phase', { ...initialPhase, resultPaths: [...initialPhase.resultPaths] });
        fixture.componentRef.setInput('index', 1);
        fixture.componentRef.setInput('isLast', false);
        fixture.detectChanges();
    });

    const getPhaseNameInput = () => fixture.debugElement.nativeElement.querySelector('#phase-name-input-1') as HTMLInputElement;
    const getScriptEditor = () => fixture.debugElement.query(By.css('[data-testid="script-editor"]'));
    const getResultPathInput = (index: number) => fixture.debugElement.nativeElement.querySelector(`[data-testid="result-path-input-${index}"]`) as HTMLInputElement | null;
    const getMoveUpButton = () => fixture.debugElement.nativeElement.querySelector(`#move-up-button-${component.index()}`) as HTMLButtonElement;
    const getMoveDownButton = () => fixture.debugElement.nativeElement.querySelector(`#move-down-button-${component.index()}`) as HTMLButtonElement;
    const getDeletePhaseButton = () => fixture.debugElement.nativeElement.querySelector(`#delete-phase-button-${component.index()}`) as HTMLButtonElement;
    const getAddResultPathButton = () => fixture.debugElement.nativeElement.querySelector(`#add-result-path-button-${component.index()}`) as HTMLButtonElement;
    const getDeleteResultPathButton = (index: number) =>
        fixture.debugElement.nativeElement.querySelector(`[data-testid="delete-result-path-button-${index}"]`) as HTMLButtonElement | null;

    describe('conditionOptions', () => {
        it('should generate options from BUILD_PHASE_CONDITION', () => {
            const options = component.conditionOptions();

            expect(options.length).toBe(Object.keys(BUILD_PHASE_CONDITION).length);
            expect(options).toContainEqual({ value: 'ALWAYS', label: 'artemisApp.programmingExercise.buildPhasesEditor.conditions.always' });
            expect(options).toContainEqual({ value: 'AFTER_DUE_DATE', label: 'artemisApp.programmingExercise.buildPhasesEditor.conditions.afterDueDate' });
            expect(options).not.toContainEqual({ value: 'FORCE_RUN', label: 'artemisApp.programmingExercise.buildPhasesEditor.conditions.forceRun' });
        });
    });

    describe('updateName', () => {
        it('should update the phase name', () => {
            const nameInput = getPhaseNameInput();
            nameInput.value = 'new-name';
            nameInput.dispatchEvent(new Event('input'));
            fixture.detectChanges();

            expect(component.phase().name).toBe('new-name');
        });

        it('should preserve other phase properties', () => {
            const nameInput = getPhaseNameInput();
            nameInput.value = 'updated';
            nameInput.dispatchEvent(new Event('input'));
            fixture.detectChanges();

            const phase = component.phase();
            expect(phase.script).toBe(initialPhase.script);
            expect(phase.condition).toBe(initialPhase.condition);
            expect(phase.forceRun).toBe(initialPhase.forceRun);
            expect(phase.resultPaths).toEqual(initialPhase.resultPaths);
        });
    });

    describe('updateScript', () => {
        it('should update the phase script', () => {
            const newScript = 'echo test';
            getScriptEditor().triggerEventHandler('textChange', newScript);
            fixture.detectChanges();

            expect(component.phase().script).toBe(newScript);
        });

        it('should preserve other phase properties', () => {
            getScriptEditor().triggerEventHandler('textChange', 'new script');
            fixture.detectChanges();

            const phase = component.phase();
            expect(phase.name).toBe(initialPhase.name);
            expect(phase.condition).toBe(initialPhase.condition);
            expect(phase.forceRun).toBe(initialPhase.forceRun);
        });
    });

    describe('updateCondition', () => {
        it('should update the phase condition', () => {
            component.updateCondition('AFTER_DUE_DATE');

            expect(component.phase().condition).toBe('AFTER_DUE_DATE');
        });

        it('should preserve other phase properties', () => {
            component.updateCondition('AFTER_DUE_DATE');

            const phase = component.phase();
            expect(phase.name).toBe(initialPhase.name);
            expect(phase.script).toBe(initialPhase.script);
            expect(phase.forceRun).toBe(initialPhase.forceRun);
        });
    });

    describe('updateForceRun', () => {
        it('should update forceRun', () => {
            component.updateForceRun(true);

            expect(component.phase().forceRun).toBe(true);
        });

        it('should preserve other phase properties', () => {
            component.updateForceRun(true);

            const phase = component.phase();
            expect(phase.name).toBe(initialPhase.name);
            expect(phase.script).toBe(initialPhase.script);
            expect(phase.condition).toBe(initialPhase.condition);
        });
    });

    describe('updateResultPath', () => {
        it('should update result path at specified index', () => {
            const resultPathInput = getResultPathInput(0)!;
            resultPathInput.value = '**/new-results.xml';
            resultPathInput.dispatchEvent(new Event('input'));
            fixture.detectChanges();

            expect(component.phase().resultPaths[0]).toBe('**/new-results.xml');
            expect(component.phase().resultPaths[1]).toBe('**/coverage.xml');
        });

        it('should update result path at last index', () => {
            const resultPathInput = getResultPathInput(1)!;
            resultPathInput.value = '**/updated.xml';
            resultPathInput.dispatchEvent(new Event('input'));
            fixture.detectChanges();

            expect(component.phase().resultPaths[0]).toBe('**/results.xml');
            expect(component.phase().resultPaths[1]).toBe('**/updated.xml');
        });

        it('should handle empty resultPaths array', () => {
            fixture.componentRef.setInput('phase', { ...initialPhase, resultPaths: [] });
            fixture.detectChanges();

            getAddResultPathButton().click();
            fixture.detectChanges();

            const resultPathInput = getResultPathInput(0)!;
            resultPathInput.value = 'new-path';
            resultPathInput.dispatchEvent(new Event('input'));
            fixture.detectChanges();

            expect(component.phase().resultPaths[0]).toBe('new-path');
        });
    });

    describe('addResultPath', () => {
        it('should add an empty result path', () => {
            const initialLength = component.phase().resultPaths.length;
            getAddResultPathButton().click();
            fixture.detectChanges();

            expect(component.phase().resultPaths.length).toBe(initialLength + 1);
            expect(component.phase().resultPaths[initialLength]).toBe('');
        });

        it('should add to empty resultPaths', () => {
            fixture.componentRef.setInput('phase', { ...initialPhase, resultPaths: [] });
            fixture.detectChanges();

            getAddResultPathButton().click();
            fixture.detectChanges();

            expect(component.phase().resultPaths.length).toBe(1);
            expect(component.phase().resultPaths[0]).toBe('');
        });

        it('should handle undefined resultPaths', () => {
            fixture.componentRef.setInput('phase', { ...initialPhase, resultPaths: undefined as any });
            fixture.detectChanges();

            getAddResultPathButton().click();
            fixture.detectChanges();

            expect(component.phase().resultPaths.length).toBe(1);
        });
    });

    describe('deleteResultPath', () => {
        it('should remove result path at specified index', () => {
            getDeleteResultPathButton(0)!.click();
            fixture.detectChanges();

            expect(component.phase().resultPaths.length).toBe(1);
            expect(component.phase().resultPaths[0]).toBe('**/coverage.xml');
        });

        it('should remove last result path', () => {
            getDeleteResultPathButton(1)!.click();
            fixture.detectChanges();

            expect(component.phase().resultPaths.length).toBe(1);
            expect(component.phase().resultPaths[0]).toBe('**/results.xml');
        });

        it('should handle deleting from single-item array', () => {
            fixture.componentRef.setInput('phase', { ...initialPhase, resultPaths: ['single'] });
            fixture.detectChanges();

            getDeleteResultPathButton(0)!.click();
            fixture.detectChanges();

            expect(component.phase().resultPaths.length).toBe(0);
        });
    });

    describe('outputs', () => {
        it('should emit delete event', () => {
            const deleteSpy = vi.fn();
            component.delete.subscribe(deleteSpy);

            getDeletePhaseButton().click();
            fixture.detectChanges();

            expect(deleteSpy).toHaveBeenCalledOnce();
        });

        it('should emit moveUp event', () => {
            const moveUpSpy = vi.fn();
            component.moveUp.subscribe(moveUpSpy);

            getMoveUpButton().click();
            fixture.detectChanges();

            expect(moveUpSpy).toHaveBeenCalledOnce();
        });

        it('should emit moveDown event', () => {
            const moveDownSpy = vi.fn();
            component.moveDown.subscribe(moveDownSpy);

            getMoveDownButton().click();
            fixture.detectChanges();

            expect(moveDownSpy).toHaveBeenCalledOnce();
        });
    });

    describe('button states', () => {
        it('should disable the move up button for the first phase', () => {
            fixture.componentRef.setInput('index', 0);
            fixture.detectChanges();

            expect(getMoveUpButton().disabled).toBe(true);
        });

        it('should disable the move down button for the last phase', () => {
            fixture.componentRef.setInput('isLast', true);
            fixture.detectChanges();

            expect(getMoveDownButton().disabled).toBe(true);
        });

        it('should disable the delete button when there is only one phase', () => {
            fixture.componentRef.setInput('index', 0);
            fixture.componentRef.setInput('isLast', true);
            fixture.detectChanges();

            expect(getDeletePhaseButton().disabled).toBe(true);
        });
    });
});
