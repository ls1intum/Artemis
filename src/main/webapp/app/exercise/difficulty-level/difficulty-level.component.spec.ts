import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DifficultyLevelComponent } from 'app/exercise/difficulty-level/difficulty-level.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('DifficultyLevelComponent', () => {
    let component: DifficultyLevelComponent;
    let fixture: ComponentFixture<DifficultyLevelComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [DifficultyLevelComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(DifficultyLevelComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should set coloredDifficultyLevel correctly for EASY', () => {
        fixture.componentRef.setInput('difficultyLevel', 'EASY');
        fixture.detectChanges();

        expect(component.coloredDifficultyLevel().label).toBe('artemisApp.exercise.easy');
        expect(component.coloredDifficultyLevel().color).toEqual(['success', 'body', 'body']);
    });

    it('should set coloredDifficultyLevel correctly for MEDIUM', () => {
        fixture.componentRef.setInput('difficultyLevel', 'MEDIUM');
        fixture.detectChanges();

        expect(component.coloredDifficultyLevel().label).toBe('artemisApp.exercise.medium');
        expect(component.coloredDifficultyLevel().color).toEqual(['warning', 'warning', 'body']);
    });

    it('should set coloredDifficultyLevel correctly for HARD', () => {
        fixture.componentRef.setInput('difficultyLevel', 'HARD');
        fixture.detectChanges();

        expect(component.coloredDifficultyLevel().label).toBe('artemisApp.exercise.hard');
        expect(component.coloredDifficultyLevel().color).toEqual(['danger', 'danger', 'danger']);
    });

    it('should set coloredDifficultyLevel correctly for no level', () => {
        fixture.componentRef.setInput('difficultyLevel', 'UNKNOWN');
        fixture.detectChanges();

        expect(component.coloredDifficultyLevel().label).toBe('artemisApp.exercise.noLevel');
        expect(component.coloredDifficultyLevel().color).toEqual(['body', 'body', 'body']);
    });

    it('should display the correct tooltip label', async () => {
        fixture.componentRef.setInput('difficultyLevel', 'EASY');
        fixture.detectChanges();

        vi.useFakeTimers();
        const tooltipElement: HTMLElement = fixture.nativeElement.querySelector('#difficulty-level');
        tooltipElement.dispatchEvent(new Event('mouseenter'));
        fixture.detectChanges();
        await vi.advanceTimersByTimeAsync(150);
        fixture.detectChanges();
        vi.useRealTimers();

        const tooltipContent = document.querySelector('[role="tooltip"]');
        expect(tooltipContent?.textContent?.trim()).toBe('artemisApp.exercise.easy');
    });

    it('should apply the correct classes for difficulty colors', () => {
        fixture.componentRef.setInput('difficultyLevel', 'EASY');
        fixture.detectChanges();

        const skillBars: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.skill-bar');
        expect(skillBars).toHaveLength(3);
        expect(skillBars[0].classList).toContain('bg-state-success');
        expect(skillBars[1].classList).toContain('bg-surface-sunken');
        expect(skillBars[2].classList).toContain('bg-surface-sunken');
    });
});
