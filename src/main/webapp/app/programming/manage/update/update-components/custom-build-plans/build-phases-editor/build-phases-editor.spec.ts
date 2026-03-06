import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { BuildPhasesEditor } from './build-phases-editor';
import { BuildPhase } from 'app/programming/shared/entities/build-plan-phases.model';
import { MockComponent, MockDirective } from 'ng-mocks';
import { BuildPhaseEditor } from './build-phase/build-phase-editor';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('BuildPhasesEditor', () => {
    setupTestBed({ zoneless: true });

    let component: BuildPhasesEditor;
    let fixture: ComponentFixture<BuildPhasesEditor>;

    const initialPhases: BuildPhase[] = [
        { name: 'build', script: 'echo build', condition: 'ALWAYS', resultPaths: [] },
        { name: 'test', script: 'echo test', condition: 'ALWAYS', resultPaths: ['**/test-results.xml'] },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildPhasesEditor],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(BuildPhasesEditor, {
                remove: { imports: [BuildPhaseEditor, TranslateDirective] },
                add: { imports: [MockComponent(BuildPhaseEditor), MockDirective(TranslateDirective)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(BuildPhasesEditor);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('phases', [...initialPhases]);
        fixture.detectChanges();
    });

    describe('phaseCount', () => {
        it('should return the correct number of phases', () => {
            expect(component.phaseCount()).toBe(2);
        });

        it('should update when phases change', () => {
            component.addPhase();
            expect(component.phaseCount()).toBe(3);
        });
    });

    describe('addPhase', () => {
        it('should add a new phase with default values', () => {
            const initialCount = component.phases().length;
            component.addPhase();

            const phases = component.phases();
            expect(phases.length).toBe(initialCount + 1);

            const newPhase = phases[phases.length - 1];
            expect(newPhase.name).toBe('');
            expect(newPhase.script).toBe('# enter the script of this phase');
            expect(newPhase.condition).toBe('ALWAYS');
            expect(newPhase.resultPaths).toEqual([]);
        });

        it('should append to existing phases', () => {
            component.addPhase();
            const phases = component.phases();

            expect(phases[0].name).toBe('build');
            expect(phases[1].name).toBe('test');
            expect(phases[2].name).toBe('');
        });
    });

    describe('deletePhase', () => {
        it('should remove phase at specified index', () => {
            component.deletePhase(0);
            const phases = component.phases();

            expect(phases.length).toBe(1);
            expect(phases[0].name).toBe('test');
        });

        it('should remove phase from the middle', () => {
            component.addPhase();
            component.phases()[2].name = 'deploy';
            component.deletePhase(1);

            const phases = component.phases();
            expect(phases.length).toBe(2);
            expect(phases[0].name).toBe('build');
        });

        it('should handle deleting the last phase', () => {
            component.deletePhase(1);
            const phases = component.phases();

            expect(phases.length).toBe(1);
            expect(phases[0].name).toBe('build');
        });
    });

    describe('updatePhase', () => {
        it('should update phase at specified index', () => {
            const updatedPhase: BuildPhase = {
                name: 'updated-build',
                script: 'npm run build',
                condition: 'AFTER_DUE_DATE',
                resultPaths: ['dist/**/*'],
            };

            component.updatePhase(0, updatedPhase);
            const phases = component.phases();

            expect(phases[0]).toEqual(updatedPhase);
            expect(phases[1].name).toBe('test');
        });

        it('should not affect other phases', () => {
            const originalSecondPhase = { ...component.phases()[1] };
            const updatedPhase: BuildPhase = {
                name: 'modified',
                script: 'modified script',
                condition: 'ALWAYS',
                resultPaths: [],
            };

            component.updatePhase(0, updatedPhase);

            expect(component.phases()[1]).toEqual(originalSecondPhase);
        });
    });

    describe('moveUp', () => {
        it('should swap phase with previous phase', () => {
            component.moveUp(1);
            const phases = component.phases();

            expect(phases[0].name).toBe('test');
            expect(phases[1].name).toBe('build');
        });

        it('should do nothing when index is 0', () => {
            const originalPhases = [...component.phases()];
            component.moveUp(0);

            expect(component.phases()[0].name).toBe(originalPhases[0].name);
            expect(component.phases()[1].name).toBe(originalPhases[1].name);
        });

        it('should correctly move middle element up', () => {
            component.addPhase();
            fixture.componentRef.setInput('phases', [
                { name: 'first', script: '', condition: 'ALWAYS', resultPaths: [] },
                { name: 'second', script: '', condition: 'ALWAYS', resultPaths: [] },
                { name: 'third', script: '', condition: 'ALWAYS', resultPaths: [] },
            ]);

            component.moveUp(2);
            const phases = component.phases();

            expect(phases[0].name).toBe('first');
            expect(phases[1].name).toBe('third');
            expect(phases[2].name).toBe('second');
        });
    });

    describe('moveDown', () => {
        it('should swap phase with next phase', () => {
            component.moveDown(0);
            const phases = component.phases();

            expect(phases[0].name).toBe('test');
            expect(phases[1].name).toBe('build');
        });

        it('should do nothing when index is last', () => {
            const originalPhases = [...component.phases()];
            component.moveDown(1);

            expect(component.phases()[0].name).toBe(originalPhases[0].name);
            expect(component.phases()[1].name).toBe(originalPhases[1].name);
        });

        it('should correctly move middle element down', () => {
            fixture.componentRef.setInput('phases', [
                { name: 'first', script: '', condition: 'ALWAYS', resultPaths: [] },
                { name: 'second', script: '', condition: 'ALWAYS', resultPaths: [] },
                { name: 'third', script: '', condition: 'ALWAYS', resultPaths: [] },
            ]);

            component.moveDown(1);
            const phases = component.phases();

            expect(phases[0].name).toBe('first');
            expect(phases[1].name).toBe('third');
            expect(phases[2].name).toBe('second');
        });
    });
});
