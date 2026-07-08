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

    it('should render the status badge (not the toggle) when only hasGraded is true', () => {
        fixture.componentRef.setInput('hasPractice', false);
        fixture.componentRef.setInput('hasGraded', true);
        fixture.detectChanges();

        expect(fixture.componentInstance.canToggle()).toBe(false);
        expect(fixture.nativeElement.querySelector('#participation-mode-toggle')).toBeNull();
        expect(fixture.nativeElement.querySelector('#participation-mode-badge')).not.toBeNull();
    });

    it('should render the status badge (not the toggle) when only hasPractice is true', () => {
        fixture.componentRef.setInput('mode', 'practice');
        fixture.componentRef.setInput('hasPractice', true);
        fixture.componentRef.setInput('hasGraded', false);
        fixture.detectChanges();

        expect(fixture.componentInstance.canToggle()).toBe(false);
        expect(fixture.nativeElement.querySelector('#participation-mode-toggle')).toBeNull();
        expect(fixture.nativeElement.querySelector('#participation-mode-badge')).not.toBeNull();
    });

    it('should render the toggle (not the badge) when both hasPractice and hasGraded are true', () => {
        fixture.componentRef.setInput('hasPractice', true);
        fixture.componentRef.setInput('hasGraded', true);
        fixture.detectChanges();

        expect(fixture.componentInstance.canToggle()).toBe(true);
        expect(fixture.nativeElement.querySelector('#participation-mode-toggle')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('#participation-mode-badge')).toBeNull();
    });

    it('should switch to practice mode via selectMode', () => {
        fixture.componentRef.setInput('hasPractice', true);
        fixture.componentRef.setInput('hasGraded', true);
        fixture.componentRef.setInput('mode', 'graded');
        fixture.detectChanges();

        fixture.componentInstance.selectMode('practice');
        fixture.detectChanges();

        expect(fixture.componentInstance.mode()).toBe('practice');
    });

    it('should switch to graded mode via selectMode', () => {
        fixture.componentRef.setInput('hasPractice', true);
        fixture.componentRef.setInput('hasGraded', true);
        fixture.componentRef.setInput('mode', 'practice');
        fixture.detectChanges();

        fixture.componentInstance.selectMode('graded');
        fixture.detectChanges();

        expect(fixture.componentInstance.mode()).toBe('graded');
    });
});
