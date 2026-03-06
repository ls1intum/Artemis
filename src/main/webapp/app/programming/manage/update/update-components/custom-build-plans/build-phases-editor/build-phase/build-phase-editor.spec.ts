import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { BuildPhaseEditor } from './build-phase-editor';
import { BUILD_PHASE_CONDITION, BuildPhase } from 'app/programming/shared/entities/build-plan-phases.model';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { MonacoEditorFitTextComponent } from '../monaco-editor-auto-size/monaco-editor-fit-text.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('BuildPhaseEditor', () => {
    setupTestBed({ zoneless: true });

    let component: BuildPhaseEditor;
    let fixture: ComponentFixture<BuildPhaseEditor>;

    const initialPhase: BuildPhase = {
        name: 'build',
        script: 'echo "building..."',
        condition: 'ALWAYS',
        resultPaths: ['**/results.xml', '**/coverage.xml'],
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildPhaseEditor],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(BuildPhaseEditor, {
                remove: { imports: [MonacoEditorFitTextComponent, TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockComponent(MonacoEditorFitTextComponent), MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(BuildPhaseEditor);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('phase', { ...initialPhase, resultPaths: [...initialPhase.resultPaths] });
        fixture.componentRef.setInput('index', 1);
        fixture.componentRef.setInput('isLast', false);
        fixture.detectChanges();
    });

    describe('conditionOptions', () => {
        it('should generate options from BUILD_PHASE_CONDITION', () => {
            const options = component.conditionOptions();

            expect(options.length).toBe(Object.keys(BUILD_PHASE_CONDITION).length);
            expect(options).toContainEqual({ value: 'ALWAYS', label: 'artemisApp.programmingExercise.buildPhasesEditor.conditions.ALWAYS' });
            expect(options).toContainEqual({ value: 'AFTER_DUE_DATE', label: 'artemisApp.programmingExercise.buildPhasesEditor.conditions.AFTER_DUE_DATE' });
        });
    });

    describe('updateName', () => {
        it('should update the phase name', () => {
            component.updateName('new-name');

            expect(component.phase().name).toBe('new-name');
        });

        it('should preserve other phase properties', () => {
            component.updateName('updated');

            const phase = component.phase();
            expect(phase.script).toBe(initialPhase.script);
            expect(phase.condition).toBe(initialPhase.condition);
            expect(phase.resultPaths).toEqual(initialPhase.resultPaths);
        });
    });

    describe('updateScript', () => {
        it('should update the phase script', () => {
            const newScript = 'echo test';
            component.updateScript(newScript);

            expect(component.phase().script).toBe(newScript);
        });

        it('should preserve other phase properties', () => {
            component.updateScript('new script');

            const phase = component.phase();
            expect(phase.name).toBe(initialPhase.name);
            expect(phase.condition).toBe(initialPhase.condition);
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
        });
    });

    describe('updateResultPath', () => {
        it('should update result path at specified index', () => {
            component.updateResultPath(0, '**/new-results.xml');

            expect(component.phase().resultPaths[0]).toBe('**/new-results.xml');
            expect(component.phase().resultPaths[1]).toBe('**/coverage.xml');
        });

        it('should update result path at last index', () => {
            component.updateResultPath(1, '**/updated.xml');

            expect(component.phase().resultPaths[0]).toBe('**/results.xml');
            expect(component.phase().resultPaths[1]).toBe('**/updated.xml');
        });

        it('should handle empty resultPaths array', () => {
            fixture.componentRef.setInput('phase', { ...initialPhase, resultPaths: [] });
            component.updateResultPath(0, 'new-path');

            expect(component.phase().resultPaths[0]).toBe('new-path');
        });
    });

    describe('addResultPath', () => {
        it('should add an empty result path', () => {
            const initialLength = component.phase().resultPaths.length;
            component.addResultPath();

            expect(component.phase().resultPaths.length).toBe(initialLength + 1);
            expect(component.phase().resultPaths[initialLength]).toBe('');
        });

        it('should add to empty resultPaths', () => {
            fixture.componentRef.setInput('phase', { ...initialPhase, resultPaths: [] });
            component.addResultPath();

            expect(component.phase().resultPaths.length).toBe(1);
            expect(component.phase().resultPaths[0]).toBe('');
        });

        it('should handle undefined resultPaths', () => {
            fixture.componentRef.setInput('phase', { ...initialPhase, resultPaths: undefined as any });
            component.addResultPath();

            expect(component.phase().resultPaths.length).toBe(1);
        });
    });

    describe('deleteResultPath', () => {
        it('should remove result path at specified index', () => {
            component.deleteResultPath(0);

            expect(component.phase().resultPaths.length).toBe(1);
            expect(component.phase().resultPaths[0]).toBe('**/coverage.xml');
        });

        it('should remove last result path', () => {
            component.deleteResultPath(1);

            expect(component.phase().resultPaths.length).toBe(1);
            expect(component.phase().resultPaths[0]).toBe('**/results.xml');
        });

        it('should handle deleting from single-item array', () => {
            fixture.componentRef.setInput('phase', { ...initialPhase, resultPaths: ['single'] });
            component.deleteResultPath(0);

            expect(component.phase().resultPaths.length).toBe(0);
        });
    });

    describe('outputs', () => {
        it('should emit delete event', () => {
            const deleteSpy = vi.fn();
            component.delete.subscribe(deleteSpy);

            component.delete.emit();

            expect(deleteSpy).toHaveBeenCalled();
        });

        it('should emit moveUp event', () => {
            const moveUpSpy = vi.fn();
            component.moveUp.subscribe(moveUpSpy);

            component.moveUp.emit();

            expect(moveUpSpy).toHaveBeenCalled();
        });

        it('should emit moveDown event', () => {
            const moveDownSpy = vi.fn();
            component.moveDown.subscribe(moveDownSpy);

            component.moveDown.emit();

            expect(moveDownSpy).toHaveBeenCalled();
        });
    });

    describe('input signals', () => {
        it('should reflect isFirst input', () => {
            fixture.componentRef.setInput('index', 0);
            expect(component.isFirst()).toBe(true);

            fixture.componentRef.setInput('index', 1);
            expect(component.isFirst()).toBe(false);
        });

        it('should reflect isLast input', () => {
            fixture.componentRef.setInput('isLast', true);
            expect(component.isLast()).toBe(true);

            fixture.componentRef.setInput('isLast', false);
            expect(component.isLast()).toBe(false);
        });

        it('should reflect isOnly input', () => {
            fixture.componentRef.setInput('index', 0);
            fixture.componentRef.setInput('isLast', true);
            expect(component.isOnly()).toBe(true);

            fixture.componentRef.setInput('isLast', false);
            expect(component.isOnly()).toBe(false);
        });
    });
});
