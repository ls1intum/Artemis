import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Component, input, output } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { CreateVariantWithAiButtonComponent } from 'app/course/manage/exercises/create-variant-modal/create-variant-with-ai-button.component';
import { ExerciseVariantAiModalWizardComponent } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal-wizard.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

@Component({ selector: 'jhi-exercise-variant-ai-modal-wizard', template: '' })
class ExerciseVariantAiModalWizardStubComponent {
    readonly visible = input(false);
    readonly visibleChange = output<boolean>();
    readonly sourceExercise = input<Exercise>();
    readonly courseId = input<number>();
    readonly examExercise = input(false);
}

describe('CreateVariantWithAiButtonComponent', () => {
    let fixture: ComponentFixture<CreateVariantWithAiButtonComponent>;

    const programmingExercise = (isAtLeastEditor: boolean) => ({ id: 1, type: ExerciseType.PROGRAMMING, isAtLeastEditor, course: { id: 2 } }) as Exercise;

    const createComponent = (exercise: Exercise, hyperionEnabled: boolean) => {
        vi.spyOn(TestBed.inject(ProfileService), 'isModuleFeatureActive').mockImplementation((feature) => hyperionEnabled && feature === MODULE_FEATURE_HYPERION);
        fixture = TestBed.createComponent(CreateVariantWithAiButtonComponent);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
    };

    const button = () => fixture.nativeElement.querySelector('[data-testid="create-variant-ai-button"]');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CreateVariantWithAiButtonComponent],
            providers: [
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(CreateVariantWithAiButtonComponent, {
                remove: { imports: [ExerciseVariantAiModalWizardComponent] },
                add: { imports: [ExerciseVariantAiModalWizardStubComponent] },
            })
            .compileComponents();
    });

    afterEach(() => vi.restoreAllMocks());

    it('renders the button for editors of a supported exercise when Hyperion is enabled', () => {
        createComponent(programmingExercise(true), true);

        expect(button()).not.toBeNull();
    });

    it('opens the wizard for the exercise when the button is clicked', () => {
        createComponent(programmingExercise(true), true);
        const wizard = () => fixture.debugElement.query(By.directive(ExerciseVariantAiModalWizardStubComponent)).componentInstance as ExerciseVariantAiModalWizardStubComponent;
        expect(wizard().visible()).toBe(false);

        button().click();
        fixture.detectChanges();

        expect(wizard().visible()).toBe(true);
        expect(wizard().sourceExercise()).toEqual(programmingExercise(true));
        expect(wizard().courseId()).toBe(2);
        expect(wizard().examExercise()).toBe(false);
    });

    it('renders nothing when Hyperion is disabled', () => {
        createComponent(programmingExercise(true), false);

        expect(button()).toBeNull();
        expect(fixture.nativeElement.querySelector('jhi-exercise-variant-ai-modal-wizard')).toBeNull();
    });

    it('renders nothing for users below editor', () => {
        createComponent(programmingExercise(false), true);

        expect(button()).toBeNull();
    });

    it('renders nothing for exercise types the generator does not support', () => {
        createComponent({ id: 1, type: ExerciseType.TEXT, isAtLeastEditor: true } as Exercise, true);

        expect(button()).toBeNull();
    });
});
