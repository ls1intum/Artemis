import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { IdeSettingsComponent } from 'app/account/user/settings/exercises/ide-preferences/ide-settings.component';
import { QuizTrainingSettingsComponent } from 'app/account/user/settings/exercises/quiz-training-settings/quiz-training-settings.component';
import { ExerciseSettingsComponent } from './exercise-settings.component';

@Component({ selector: 'jhi-ide-preferences', template: '' })
class MockIdeSettingsComponent {}

@Component({ selector: 'jhi-quiz-training-settings', template: '' })
class MockQuizTrainingSettingsComponent {}

describe('ExerciseSettingsComponent', () => {
    let fixture: ComponentFixture<ExerciseSettingsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseSettingsComponent],
        })
            .overrideComponent(ExerciseSettingsComponent, {
                remove: { imports: [IdeSettingsComponent, QuizTrainingSettingsComponent] },
                add: { imports: [MockIdeSettingsComponent, MockQuizTrainingSettingsComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseSettingsComponent);
        fixture.detectChanges();
    });

    it('renders IDE Preferences and Quiz Training', () => {
        expect(fixture.nativeElement.querySelector('jhi-ide-preferences')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('jhi-quiz-training-settings')).toBeTruthy();
    });
});
