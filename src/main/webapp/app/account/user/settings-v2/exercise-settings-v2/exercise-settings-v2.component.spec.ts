import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { IdeSettingsComponent } from 'app/account/user/settings/ide-preferences/ide-settings.component';
import { QuizTrainingSettingsComponent } from 'app/account/user/settings/quiz-training-settings/quiz-training-settings.component';
import { ExerciseSettingsV2Component } from './exercise-settings-v2.component';

@Component({ selector: 'jhi-ide-preferences', template: '' })
class MockIdeSettingsComponent {}

@Component({ selector: 'jhi-quiz-training-settings', template: '' })
class MockQuizTrainingSettingsComponent {}

describe('ExerciseSettingsV2Component', () => {
    let fixture: ComponentFixture<ExerciseSettingsV2Component>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseSettingsV2Component],
        })
            .overrideComponent(ExerciseSettingsV2Component, {
                remove: { imports: [IdeSettingsComponent, QuizTrainingSettingsComponent] },
                add: { imports: [MockIdeSettingsComponent, MockQuizTrainingSettingsComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseSettingsV2Component);
        fixture.detectChanges();
    });

    it('renders IDE Preferences and Quiz Training', () => {
        expect(fixture.nativeElement.querySelector('jhi-ide-preferences')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('jhi-quiz-training-settings')).toBeTruthy();
    });
});
