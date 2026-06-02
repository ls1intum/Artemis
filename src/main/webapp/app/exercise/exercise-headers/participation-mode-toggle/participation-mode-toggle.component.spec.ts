import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective } from 'ng-mocks';
import { ParticipationModeToggleComponent } from 'app/exercise/exercise-headers/participation-mode-toggle/participation-mode-toggle.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

describe('ParticipationModeToggleComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ParticipationModeToggleComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ParticipationModeToggleComponent],
        });

        TestBed.overrideComponent(ParticipationModeToggleComponent, {
            remove: { imports: [TranslateDirective] },
            add: { imports: [MockDirective(TranslateDirective)] },
        });

        fixture = TestBed.createComponent(ParticipationModeToggleComponent);
        fixture.componentRef.setInput('mode', 'graded');
        fixture.detectChanges();
    });

    it('should render nothing when neither hasPractice nor hasGraded', () => {
        fixture.componentRef.setInput('hasPractice', false);
        fixture.componentRef.setInput('hasGraded', false);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('#participation-mode-toggle')).toBeNull();
        expect(fixture.nativeElement.querySelector('#participation-mode-badge')).toBeNull();
    });

    it('should render only the graded badge when only hasGraded is true', () => {
        fixture.componentRef.setInput('hasPractice', false);
        fixture.componentRef.setInput('hasGraded', true);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('#participation-mode-toggle')).toBeNull();
        expect(fixture.nativeElement.querySelector('#participation-mode-badge')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('#practice-mode-button')).toBeNull();
        expect(fixture.nativeElement.querySelector('#graded-mode-button')).toBeNull();
    });

    it('should render only the practice badge when only hasPractice is true', () => {
        fixture.componentRef.setInput('mode', 'practice');
        fixture.componentRef.setInput('hasPractice', true);
        fixture.componentRef.setInput('hasGraded', false);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('#participation-mode-toggle')).toBeNull();
        expect(fixture.nativeElement.querySelector('#participation-mode-badge')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('#practice-mode-button')).toBeNull();
        expect(fixture.nativeElement.querySelector('#graded-mode-button')).toBeNull();
    });

    it('should render two clickable buttons when both hasPractice and hasGraded are true', () => {
        fixture.componentRef.setInput('hasPractice', true);
        fixture.componentRef.setInput('hasGraded', true);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('#participation-mode-toggle')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('#practice-mode-button')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('#graded-mode-button')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('#participation-mode-badge')).toBeNull();
    });

    it('should mark graded button as active when mode is graded', () => {
        fixture.componentRef.setInput('hasPractice', true);
        fixture.componentRef.setInput('hasGraded', true);
        fixture.componentRef.setInput('mode', 'graded');
        fixture.detectChanges();

        const practiceButton: HTMLButtonElement = fixture.nativeElement.querySelector('#practice-mode-button');
        const gradedButton: HTMLButtonElement = fixture.nativeElement.querySelector('#graded-mode-button');
        expect(practiceButton.classList).not.toContain('segmented-control__button--active');
        expect(gradedButton.classList).toContain('segmented-control__button--active');
    });

    it('should mark practice button as active when mode is practice', () => {
        fixture.componentRef.setInput('hasPractice', true);
        fixture.componentRef.setInput('hasGraded', true);
        fixture.componentRef.setInput('mode', 'practice');
        fixture.detectChanges();

        const practiceButton: HTMLButtonElement = fixture.nativeElement.querySelector('#practice-mode-button');
        const gradedButton: HTMLButtonElement = fixture.nativeElement.querySelector('#graded-mode-button');
        expect(practiceButton.classList).toContain('segmented-control__button--active');
        expect(gradedButton.classList).not.toContain('segmented-control__button--active');
    });

    it('should switch to practice mode when practice button is clicked', () => {
        fixture.componentRef.setInput('hasPractice', true);
        fixture.componentRef.setInput('hasGraded', true);
        fixture.componentRef.setInput('mode', 'graded');
        fixture.detectChanges();

        fixture.nativeElement.querySelector('#practice-mode-button').click();
        fixture.detectChanges();

        expect(fixture.componentInstance.mode()).toBe('practice');
    });

    it('should switch to graded mode when graded button is clicked', () => {
        fixture.componentRef.setInput('hasPractice', true);
        fixture.componentRef.setInput('hasGraded', true);
        fixture.componentRef.setInput('mode', 'practice');
        fixture.detectChanges();

        fixture.nativeElement.querySelector('#graded-mode-button').click();
        fixture.detectChanges();

        expect(fixture.componentInstance.mode()).toBe('graded');
    });
});
