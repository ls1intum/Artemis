import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective } from 'ng-mocks';
import { Subject, of } from 'rxjs';
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
    it('waits for authoritative programming capability and passes the selected destination context to the wizard', async () => {
        const response = new Subject<ExerciseGenerationCapabilities>();
        capabilities.mockReturnValue(response);
        const source = programming();
        const fixture = mount(source);
        expect(fixture.nativeElement.querySelector('button')).toBeNull();
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
        expect(fixture.nativeElement.querySelector('button')).toBeNull();
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
    it('hides unsupported configurations before requesting capabilities', () => {
        const fixture = mount(Object.assign(programming(), { projectType: ProjectType.PLAIN_MAVEN }));
        expect(fixture.nativeElement.querySelector('button')).toBeNull();
        expect(capabilities).not.toHaveBeenCalled();
    });
});
