import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TumUiButtonComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';
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
                remove: { imports: [TumUiButtonComponent, TumUiTooltipDirective, TranslateDirective, ArtemisTranslatePipe, HelpIconComponent, BuildPhasesEditorComponent] },
                add: {
                    imports: [
                        MockComponent(TumUiButtonComponent),
                        MockDirective(TumUiTooltipDirective),
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
});
