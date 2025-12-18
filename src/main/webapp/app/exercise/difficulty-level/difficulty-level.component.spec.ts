import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DifficultyLevelComponent } from 'app/exercise/difficulty-level/difficulty-level.component';

describe('DifficultyLevelComponent', () => {
    let component: DifficultyLevelComponent;
    let fixture: ComponentFixture<DifficultyLevelComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [DifficultyLevelComponent, NgbTooltipModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(DifficultyLevelComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should set coloredDifficultyLevel correctly for EASY', () => {
        component.difficultyLevel = 'EASY';
        fixture.changeDetectorRef.detectChanges();

        expect(component.coloredDifficultyLevel.label).toBe('artemisApp.exercise.easy');
        expect(component.coloredDifficultyLevel.color).toEqual(['success', 'body', 'body']);
    });

    it('should set coloredDifficultyLevel correctly for MEDIUM', () => {
        component.difficultyLevel = 'MEDIUM';
        fixture.changeDetectorRef.detectChanges();

        expect(component.coloredDifficultyLevel.label).toBe('artemisApp.exercise.medium');
        expect(component.coloredDifficultyLevel.color).toEqual(['warning', 'warning', 'body']);
    });

    it('should set coloredDifficultyLevel correctly for HARD', () => {
        component.difficultyLevel = 'HARD';
        fixture.changeDetectorRef.detectChanges();

        expect(component.coloredDifficultyLevel.label).toBe('artemisApp.exercise.hard');
        expect(component.coloredDifficultyLevel.color).toEqual(['danger', 'danger', 'danger']);
    });

    it('should set coloredDifficultyLevel correctly for no level', () => {
        component.difficultyLevel = 'UNKNOWN';
        fixture.changeDetectorRef.detectChanges();

        expect(component.coloredDifficultyLevel.label).toBe('artemisApp.exercise.noLevel');
        expect(component.coloredDifficultyLevel.color).toEqual(['body', 'body', 'body']);
    });
    it('should display the correct tooltip label', () => {
        component.difficultyLevel = 'EASY';
        fixture.changeDetectorRef.detectChanges();

        const tooltipElement: HTMLElement = fixture.nativeElement.querySelector('#difficulty-level');
        tooltipElement.dispatchEvent(new Event('mouseenter'));
        fixture.detectChanges();

        const tooltipContent = document.querySelector('.tooltip-inner');
        expect(tooltipContent?.textContent).toBe('artemisApp.exercise.easy');
    });

    it('should apply the correct classes for difficulty colors', () => {
        component.difficultyLevel = 'EASY';
        fixture.changeDetectorRef.detectChanges();
        const skillBars: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.skill-bar');
        expect(skillBars).toHaveLength(3);
        expect(skillBars[0].classList).toContain('bg-success');
        expect(skillBars[1].classList).toContain('bg-body');
        expect(skillBars[2].classList).toContain('bg-body');
    });
});
