import { TumAetUiButtonComponent } from '@tumaet/ui-angular';
import { faRobot } from '@fortawesome/free-solid-svg-icons';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MODULE_FEATURE_HYPERION_EXERCISE_GENERATION } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HyperionExerciseGenerationApi } from 'app/openapi/api/hyperion-exercise-generation-api';
import { ExerciseGenerationCapabilities } from 'app/openapi/model/exercise-generation-capabilities';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { CreateVariantWithAiButtonComponent } from './create-variant-with-ai-button.component';
import { ExerciseVariantAiModalWizardComponent } from './exercise-variant-ai-modal-wizard.component';

describe('CreateVariantWithAiButtonComponent', () => {
    const capabilities = vi.fn();
    const moduleFeatureActive = vi.fn().mockReturnValue(true);
    const programming = () =>
        Object.assign(new ProgrammingExercise(undefined, undefined), {
            id: 12,
            programmingLanguage: ProgrammingLanguage.JAVA,
            projectType: ProjectType.PLAIN_GRADLE,
            isAtLeastEditor: true,
            course: { id: 4 },
        });
    beforeEach(() => {
        capabilities.mockReset().mockReturnValue(of({ canCreateVariant: true }));
        moduleFeatureActive.mockReset().mockReturnValue(true);
        TestBed.configureTestingModule({
            providers: [
                { provide: ProfileService, useValue: { isModuleFeatureActive: moduleFeatureActive } },
                { provide: HyperionExerciseGenerationApi, useValue: { getGenerationCapabilities: capabilities } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).overrideComponent(CreateVariantWithAiButtonComponent, {
            remove: { imports: [ExerciseVariantAiModalWizardComponent, TranslateDirective] },
            add: { imports: [MockComponent(ExerciseVariantAiModalWizardComponent), MockDirective(TranslateDirective)] },
        });
    });
    afterEach(() => TestBed.resetTestingModule());
    const mount = (exercise: Exercise) => {
        const fixture = TestBed.createComponent(CreateVariantWithAiButtonComponent);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        TestBed.tick();
        fixture.detectChanges();
        return fixture;
    };
    it.each(['programming', 'quiz'])('keeps the compact warning action style for %s detail pages', (type) => {
        const source = type === 'programming' ? programming() : ({ id: 13, type: ExerciseType.QUIZ, isAtLeastEditor: true } as Exercise);
        const fixture = mount(source);
        const button = fixture.debugElement.query(By.directive(TumAetUiButtonComponent)).componentInstance as TumAetUiButtonComponent;
        expect(button.severity()).toBe('warn');
        expect(button.variant()).toBe('solid');
        expect(button.size()).toBe('small');
        expect(button.icon()).toBe(faRobot);
        expect(fixture.nativeElement.querySelector('[data-testid="create-variant-ai-button"]').textContent).not.toContain('Artemis Intelligence');
    });

    it('waits for authoritative programming capability and passes the selected destination context to the wizard', async () => {
        const response = new Subject<ExerciseGenerationCapabilities>();
        capabilities.mockReturnValue(response);
        const source = programming();
        const fixture = mount(source);
        expect(fixture.nativeElement.querySelector('button').disabled).toBe(true);
        expect(fixture.componentInstance.blocker()).toBe('artemisApp.hyperion.generation.blocker.capabilitiesLoading');
        response.next({ canCreateVariant: true });
        await fixture.whenStable();
        fixture.detectChanges();
        fixture.nativeElement.querySelector('button').click();
        fixture.detectChanges();
        const wizard = fixture.debugElement.query(By.directive(ExerciseVariantAiModalWizardComponent)).componentInstance;
        expect(wizard.visible()).toBe(true);
        expect(wizard.sourceExercise()).toBe(source);
        expect(wizard.courseId()).toBe(4);
        expect(wizard.examExercise()).toBe(false);
        wizard.visibleChange.emit(false);
        fixture.detectChanges();
        expect(wizard.visible()).toBe(false);
        response.next({ canCreateVariant: false });
        await fixture.whenStable();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('button').disabled).toBe(true);
        expect(fixture.componentInstance.blocker()).toBe('artemisApp.hyperion.generation.blocker.variantUnavailable');
    });
    it.each([false, undefined])('does not offer variants or request capabilities without editor permission (%s)', (isAtLeastEditor) => {
        const fixture = mount(Object.assign(programming(), { isAtLeastEditor }));
        expect(fixture.nativeElement.querySelector('button')).toBeNull();
        expect(capabilities).not.toHaveBeenCalled();
    });
    it('preserves quiz behavior and resolves preparatory exam course context without programming requests', () => {
        const fixture = mount({ id: 13, type: ExerciseType.QUIZ, isAtLeastEditor: true, exerciseGroup: { exam: { course: { id: 7 } } } } as Exercise);
        expect(fixture.nativeElement.querySelector('button')).not.toBeNull();
        expect(fixture.componentInstance.isExamExercise()).toBe(true);
        expect(fixture.componentInstance.resolvedCourseId()).toBe(7);
        expect(capabilities).not.toHaveBeenCalled();
    });
    it('hides the quiz variant action when Hyperion is disabled', () => {
        moduleFeatureActive.mockReturnValue(false);
        const fixture = mount({ id: 13, type: ExerciseType.QUIZ, isAtLeastEditor: true } as Exercise);
        expect(fixture.nativeElement.querySelector('button')).toBeNull();
        expect(capabilities).not.toHaveBeenCalled();
    });
    it('shows an unsupported configuration reason without requesting capabilities', () => {
        const fixture = mount(Object.assign(programming(), { projectType: ProjectType.MAVEN_BLACKBOX }));
        expect(fixture.nativeElement.querySelector('button').disabled).toBe(true);
        expect(fixture.componentInstance.blocker()).toBe('artemisApp.hyperion.generation.blocker.unsupportedProjectType');
        expect(capabilities).not.toHaveBeenCalled();
    });
    it('explains a blocked variant only as a tooltip, never as a line of text under the button', () => {
        const fixture = mount(programming());
        fixture.componentRef.setInput('disabledReason', 'artemisApp.hyperion.generation.blocker.saveBeforeVariant');
        fixture.detectChanges();
        const wrapper = fixture.nativeElement.querySelector('[data-testid="create-variant-ai"]') as HTMLElement;
        expect(wrapper.getAttribute('title')).toBe('artemisApp.hyperion.generation.blocker.saveBeforeVariant');
        expect(fixture.nativeElement.querySelector('[data-testid="create-variant-ai-reason"]')).toBeNull();
        expect(wrapper.textContent).not.toContain('saveBeforeVariant');
    });
    it('keeps a failed capability lookup visible and retries without opening the wizard', async () => {
        capabilities.mockReturnValueOnce(throwError(() => new Error('network'))).mockReturnValue(of({ canCreateVariant: true }));
        const fixture = mount(programming());
        await fixture.whenStable();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('button').disabled).toBe(true);
        fixture.componentInstance.openWizard();
        expect(fixture.debugElement.query(By.directive(ExerciseVariantAiModalWizardComponent)).componentInstance.visible()).toBe(false);
        fixture.nativeElement.querySelector('[data-testid="create-variant-ai-retry"] button').click();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(capabilities).toHaveBeenCalledTimes(2);
        expect(fixture.nativeElement.querySelector('button').disabled).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="create-variant-ai-retry"]')).toBeNull();
    });

    it('requires settings to be saved before opening the wizard', () => {
        const fixture = mount(programming());
        fixture.componentRef.setInput('disabledReason', 'artemisApp.hyperion.generation.blocker.saveBeforeVariant');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('button').disabled).toBe(true);
        fixture.componentInstance.openWizard();
        expect(fixture.debugElement.query(By.directive(ExerciseVariantAiModalWizardComponent)).componentInstance.visible()).toBe(false);
    });

    it('offers variants of released and participated course sources when the server permits them', () => {
        const fixture = mount(Object.assign(programming(), { releaseDate: dayjs().subtract(1, 'day'), numberOfParticipations: 3 }));
        expect(fixture.nativeElement.querySelector('button').disabled).toBe(false);
    });

    it('does not add a programming exam surface or send a capability request', () => {
        const fixture = mount(Object.assign(programming(), { exerciseGroup: { exam: { course: { id: 7 } } } }));
        expect(fixture.nativeElement.querySelector('button')).toBeNull();
        expect(capabilities).not.toHaveBeenCalled();
    });

    it('does not request programming capabilities when Hyperion is disabled', () => {
        moduleFeatureActive.mockReturnValue(false);
        const fixture = mount(programming());
        expect(fixture.nativeElement.querySelector('button')).toBeNull();
        expect(capabilities).not.toHaveBeenCalled();
    });

    it('keeps programming variants off when whole-exercise generation is disabled', () => {
        moduleFeatureActive.mockImplementation((feature: string) => feature !== MODULE_FEATURE_HYPERION_EXERCISE_GENERATION);
        const fixture = mount(programming());
        expect(fixture.nativeElement.querySelector('button')).toBeNull();
        expect(capabilities).not.toHaveBeenCalled();
    });
});
