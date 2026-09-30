import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TumAetUiButtonComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { BuildContainerEditorComponent } from './build-container-editor.component';
import { BuildPhasesEditorComponent } from 'app/programming/manage/build-plan-editor/build-phases-editor/build-phases-editor.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { BuildContainer } from 'app/programming/shared/entities/build-plan-phases.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('BuildContainerEditorComponent', () => {
    let fixture: ComponentFixture<BuildContainerEditorComponent>;
    let component: BuildContainerEditorComponent;

    const container: BuildContainer = {
        name: 'student_tests',
        dockerImage: 'image-a:1',
        repositories: [{ type: 'USER' }],
        phases: [{ name: 'test', script: 'echo test', condition: 'ALWAYS', forceRun: false, resultPaths: [] }],
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildContainerEditorComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(BuildContainerEditorComponent, {
                remove: { imports: [TumAetUiButtonComponent, TumAetUiTooltipDirective, TranslateDirective, ArtemisTranslatePipe, HelpIconComponent, BuildPhasesEditorComponent] },
                add: {
                    imports: [
                        MockComponent(TumAetUiButtonComponent),
                        MockDirective(TumAetUiTooltipDirective),
                        MockDirective(TranslateDirective),
                        MockPipe(ArtemisTranslatePipe, (key: string) => key),
                        MockComponent(HelpIconComponent),
                        MockComponent(BuildPhasesEditorComponent),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(BuildContainerEditorComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('container', { ...container, phases: [...container.phases] });
    });

    const getRemoveButton = () => fixture.debugElement.query(By.css('[data-testid="remove-container-button"]'));

    it('should emit remove when the remove button is clicked', () => {
        fixture.componentRef.setInput('canRemove', true);
        fixture.detectChanges();
        const removeSpy = vi.fn();
        component.remove.subscribe(removeSpy);

        // the button is a mocked TUM UI component, so its clicked output is triggered rather than the DOM click
        getRemoveButton().triggerEventHandler('clicked', undefined);

        expect(removeSpy).toHaveBeenCalledOnce();
    });

    it('should not offer the remove button for the last container', () => {
        fixture.componentRef.setInput('canRemove', false);
        fixture.detectChanges();

        expect(getRemoveButton()).toBeNull();
    });

    it('should flag a duplicate container name regardless of case', () => {
        fixture.componentRef.setInput('otherContainerNames', ['Student_Tests']);
        fixture.detectChanges();

        expect(component.isNameUnique()).toBe(false);
        expect(component.nameValidationMessageKey()).toBe('artemisApp.programmingExercise.buildContainersEditor.containerNameDuplicate');
    });

    describe('Docker flags', () => {
        const getEnvVarRows = () => fixture.debugElement.queryAll(By.css('[data-testid="container-env-var-row"]'));
        const getField = (id: string) => fixture.debugElement.query(By.css('#' + id));

        it('should hide the flags of a container that runs with the flags of the exercise', () => {
            fixture.detectChanges();

            expect(component.overridesDockerFlags()).toBe(false);
            expect(getField('field_container_cpu_count_student_tests')).toBeNull();
        });

        it('should show the flags once the container overrides them and discard them when the override is switched off', () => {
            fixture.detectChanges();

            component.toggleDockerFlags(true);
            fixture.detectChanges();
            expect(component.container().dockerFlags).toEqual({});
            expect(getField('field_container_cpu_count_student_tests')).not.toBeNull();

            component.setCpuCount(2);
            component.toggleDockerFlags(false);
            fixture.detectChanges();
            expect(component.container().dockerFlags).toBeUndefined();
            expect(getField('field_container_cpu_count_student_tests')).toBeNull();
        });

        it('should show the flags of a container that arrives with an override', () => {
            fixture.componentRef.setInput('container', { ...container, dockerFlags: { cpuCount: 2, env: { MODE: 'strict' } } });
            fixture.detectChanges();

            expect(component.overridesDockerFlags()).toBe(true);
            expect(getEnvVarRows()).toHaveLength(1);
        });

        it('should offer the network only where the language supports selecting one', () => {
            fixture.componentRef.setInput('container', { ...container, dockerFlags: {} });
            fixture.detectChanges();
            expect(getField('field_container_network_student_tests')).toBeNull();

            fixture.componentRef.setInput('customNetworks', ['none', 'custom']);
            fixture.detectChanges();
            // the empty option stands for the network of the exercise
            expect(getField('field_container_network_student_tests').queryAll(By.css('option'))).toHaveLength(3);

            component.setNetwork('none');
            expect(component.container().dockerFlags?.network).toBe('none');
            component.setNetwork('');
            expect(component.container().dockerFlags?.network).toBeUndefined();
        });

        it('should take a resource limit from its field and drop it again when the field is emptied', () => {
            fixture.componentRef.setInput('container', { ...container, dockerFlags: {} });
            fixture.detectChanges();

            component.setCpuCount(2);
            component.setMemory('512');
            component.setMemorySwap(0);
            expect(component.container().dockerFlags).toEqual({ cpuCount: 2, memory: 512, memorySwap: 0 });

            component.setMemory('');
            component.setCpuCount(null);
            expect(component.container().dockerFlags).toEqual({ cpuCount: undefined, memory: undefined, memorySwap: 0 });
        });

        it('should mark a resource limit outside the bounds of the server', () => {
            fixture.componentRef.setInput('container', { ...container, dockerFlags: { cpuCount: 0, memory: 5, memorySwap: -1 } });
            fixture.detectChanges();

            expect(component.isCpuCountValid()).toBe(false);
            expect(component.isMemoryValid()).toBe(false);
            expect(component.isMemorySwapValid()).toBe(false);
            expect(getField('field_container_cpu_count_student_tests').nativeElement.classList.contains('is-invalid')).toBe(true);

            component.setCpuCount(1);
            component.setMemory(6);
            component.setMemorySwap(0);
            expect(component.isCpuCountValid()).toBe(true);
            expect(component.isMemoryValid()).toBe(true);
            expect(component.isMemorySwapValid()).toBe(true);
        });

        it('should keep an environment variable row without a key out of the container until it has one', () => {
            fixture.componentRef.setInput('container', { ...container, dockerFlags: { env: { MODE: 'strict' } } });
            fixture.detectChanges();

            component.addEnvVar();
            fixture.detectChanges();
            // the new row is shown, but a variable without a name is not part of the container's flags
            expect(getEnvVarRows()).toHaveLength(2);
            expect(component.container().dockerFlags?.env).toEqual({ MODE: 'strict' });

            component.setEnvVarValue(1, 'info');
            fixture.detectChanges();
            expect(getEnvVarRows()).toHaveLength(2);
            expect(component.container().dockerFlags?.env).toEqual({ MODE: 'strict' });

            component.setEnvVarKey(1, 'LOG_LEVEL');
            fixture.detectChanges();
            expect(getEnvVarRows()).toHaveLength(2);
            expect(component.container().dockerFlags?.env).toEqual({ MODE: 'strict', LOG_LEVEL: 'info' });
        });

        it('should remove an environment variable and leave no empty map behind', () => {
            fixture.componentRef.setInput('container', { ...container, dockerFlags: { env: { MODE: 'strict' } } });
            fixture.detectChanges();

            component.removeEnvVar(0);
            fixture.detectChanges();

            expect(getEnvVarRows()).toHaveLength(0);
            expect(component.container().dockerFlags?.env).toBeUndefined();
        });

        it('should show the variables of another container that takes this editor', () => {
            // the editors are tracked by position, so removing a container hands its editor the next container
            fixture.componentRef.setInput('container', { ...container, dockerFlags: { env: { MODE: 'strict' } } });
            fixture.detectChanges();
            component.addEnvVar();

            fixture.componentRef.setInput('container', { ...container, name: 'other', dockerFlags: { env: { A: '1', B: '2' } } });
            fixture.detectChanges();

            expect(getEnvVarRows()).toHaveLength(2);
        });
    });
});
