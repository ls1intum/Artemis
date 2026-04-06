import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { BuildPhasesEditorComponent } from './build-phases-editor.component';
import { BuildPhase } from 'app/programming/shared/entities/build-plan-phases.model';
import { MockComponent, MockDirective } from 'ng-mocks';
import { BuildPhaseEditorComponent } from './build-phase/build-phase-editor.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('BuildPhasesEditorComponent', () => {
    setupTestBed({ zoneless: true });

    let component: BuildPhasesEditorComponent;
    let fixture: ComponentFixture<BuildPhasesEditorComponent>;

    const initialPhases: BuildPhase[] = [
        { name: 'build', script: 'echo build', condition: 'ALWAYS', forceRun: false, resultPaths: [] },
        { name: 'test', script: 'echo test', condition: 'ALWAYS', forceRun: false, resultPaths: ['**/test-results.xml'] },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildPhasesEditorComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(BuildPhasesEditorComponent, {
                remove: { imports: [BuildPhaseEditorComponent, TranslateDirective] },
                add: { imports: [MockComponent(BuildPhaseEditorComponent), MockDirective(TranslateDirective)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(BuildPhasesEditorComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('phases', [...initialPhases]);
        fixture.detectChanges();
    });

    const getBuildPhaseEditors = () => fixture.debugElement.queryAll(By.directive(BuildPhaseEditorComponent));
    const getAddPhaseButton = () => fixture.debugElement.nativeElement.querySelector('#add-phase-button') as HTMLButtonElement;

    describe('phaseCount', () => {
        it('should return the correct number of phases', () => {
            expect(getBuildPhaseEditors()).toHaveLength(2);
        });

        it('should update when phases change', () => {
            getAddPhaseButton().click();
            fixture.detectChanges();

            expect(getBuildPhaseEditors()).toHaveLength(3);
        });
    });

    describe('addPhase', () => {
        it('should add a new phase with default values', () => {
            const initialCount = component.phases().length;
            getAddPhaseButton().click();
            fixture.detectChanges();

            const phases = component.phases();
            expect(phases.length).toBe(initialCount + 1);

            const newPhase = phases[phases.length - 1];
            expect(newPhase.name).toBe('');
            expect(newPhase.script).toBe('# enter the script of this phase');
            expect(newPhase.condition).toBe('ALWAYS');
            expect(newPhase.forceRun).toBe(false);
            expect(newPhase.resultPaths).toEqual([]);
        });

        it('should append to existing phases', () => {
            getAddPhaseButton().click();
            fixture.detectChanges();

            const phases = component.phases();

            expect(phases[0].name).toBe('build');
            expect(phases[1].name).toBe('test');
            expect(phases[2].name).toBe('');
        });
    });

    describe('deletePhase', () => {
        it('should remove phase at specified index', () => {
            getBuildPhaseEditors()[0].triggerEventHandler('delete', undefined);
            fixture.detectChanges();

            const phases = component.phases();

            expect(phases.length).toBe(1);
            expect(phases[0].name).toBe('test');
        });

        it('should remove phase from the middle', () => {
            getAddPhaseButton().click();
            fixture.detectChanges();

            component.phases()[2].name = 'deploy';
            getBuildPhaseEditors()[1].triggerEventHandler('delete', undefined);
            fixture.detectChanges();

            const phases = component.phases();
            expect(phases.length).toBe(2);
            expect(phases[0].name).toBe('build');
        });

        it('should handle deleting the last phase', () => {
            getBuildPhaseEditors()[1].triggerEventHandler('delete', undefined);
            fixture.detectChanges();

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
                forceRun: false,
                resultPaths: ['dist/**/*'],
            };

            getBuildPhaseEditors()[0].triggerEventHandler('phaseChange', updatedPhase);
            fixture.detectChanges();

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
                forceRun: false,
                resultPaths: [],
            };

            getBuildPhaseEditors()[0].triggerEventHandler('phaseChange', updatedPhase);
            fixture.detectChanges();

            expect(component.phases()[1]).toEqual(originalSecondPhase);
        });
    });

    describe('moveUp', () => {
        it('should swap phase with previous phase', () => {
            getBuildPhaseEditors()[1].triggerEventHandler('moveUp', undefined);
            fixture.detectChanges();

            const phases = component.phases();

            expect(phases[0].name).toBe('test');
            expect(phases[1].name).toBe('build');
        });

        it('should do nothing when index is 0', () => {
            const originalPhases = [...component.phases()];
            getBuildPhaseEditors()[0].triggerEventHandler('moveUp', undefined);
            fixture.detectChanges();

            expect(component.phases()[0].name).toBe(originalPhases[0].name);
            expect(component.phases()[1].name).toBe(originalPhases[1].name);
        });

        it('should correctly move middle element up', () => {
            getAddPhaseButton().click();
            fixture.detectChanges();

            fixture.componentRef.setInput('phases', [
                { name: 'first', script: '', condition: 'ALWAYS', forceRun: false, resultPaths: [] },
                { name: 'second', script: '', condition: 'ALWAYS', forceRun: false, resultPaths: [] },
                { name: 'third', script: '', condition: 'ALWAYS', forceRun: false, resultPaths: [] },
            ]);
            fixture.detectChanges();

            getBuildPhaseEditors()[2].triggerEventHandler('moveUp', undefined);
            fixture.detectChanges();

            const phases = component.phases();

            expect(phases[0].name).toBe('first');
            expect(phases[1].name).toBe('third');
            expect(phases[2].name).toBe('second');
        });
    });

    describe('moveDown', () => {
        it('should swap phase with next phase', () => {
            getBuildPhaseEditors()[0].triggerEventHandler('moveDown', undefined);
            fixture.detectChanges();

            const phases = component.phases();

            expect(phases[0].name).toBe('test');
            expect(phases[1].name).toBe('build');
        });

        it('should do nothing when index is last', () => {
            const originalPhases = [...component.phases()];
            getBuildPhaseEditors()[1].triggerEventHandler('moveDown', undefined);
            fixture.detectChanges();

            expect(component.phases()[0].name).toBe(originalPhases[0].name);
            expect(component.phases()[1].name).toBe(originalPhases[1].name);
        });

        it('should correctly move middle element down', () => {
            fixture.componentRef.setInput('phases', [
                { name: 'first', script: '', condition: 'ALWAYS', forceRun: false, resultPaths: [] },
                { name: 'second', script: '', condition: 'ALWAYS', forceRun: false, resultPaths: [] },
                { name: 'third', script: '', condition: 'ALWAYS', forceRun: false, resultPaths: [] },
            ]);
            fixture.detectChanges();

            getBuildPhaseEditors()[1].triggerEventHandler('moveDown', undefined);
            fixture.detectChanges();

            const phases = component.phases();

            expect(phases[0].name).toBe('first');
            expect(phases[1].name).toBe('third');
            expect(phases[2].name).toBe('second');
        });
    });
});
