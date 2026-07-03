import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateService } from '@ngx-translate/core';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { ExerciseAddModalComponent } from 'app/course/manage/exercises/create-modal/exercise-add-modal.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('ExerciseAddModalComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseAddModalComponent>;
    let component: ExerciseAddModalComponent;
    let profileService: ProfileService;
    let featureToggleService: MockFeatureToggleService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseAddModalComponent],
            providers: [
                provideRouter([]),
                provideNoopAnimations(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: DialogService, useClass: MockDialogService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
            ],
        }).compileComponents();

        profileService = TestBed.inject(ProfileService);
        featureToggleService = TestBed.inject(FeatureToggleService) as unknown as MockFeatureToggleService;
    });

    /** The gating is resolved at construction (toSignal + computed), so configure the mocks before creating the component. */
    function createComponent(): void {
        fixture = TestBed.createComponent(ExerciseAddModalComponent);
        component = fixture.componentInstance;
    }

    function visibleCardTypes(): ExerciseType[] {
        // exerciseTypeCards is a protected computed signal; read it directly for the gating assertion.
        return (component as unknown as { exerciseTypeCards: () => { type: ExerciseType }[] }).exerciseTypeCards().map((card) => card.type);
    }

    it('shows every exercise type when all module features and the programming toggle are active', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        createComponent();
        expect(visibleCardTypes()).toEqual([ExerciseType.PROGRAMMING, ExerciseType.QUIZ, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD]);
    });

    it('hides text, modeling and file-upload when their module feature is inactive', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);
        createComponent();
        // Quiz is not module-gated and programming stays because its feature toggle defaults to active.
        expect(visibleCardTypes()).toEqual([ExerciseType.PROGRAMMING, ExerciseType.QUIZ]);
    });

    it('hides programming while its feature toggle is off', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        featureToggleService.setFeatureToggleState(FeatureToggle.ProgrammingExercises, false);
        createComponent();
        expect(visibleCardTypes()).not.toContain(ExerciseType.PROGRAMMING);
    });
});
