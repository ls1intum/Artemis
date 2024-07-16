import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

import { DifficultyLevelComponent } from 'app/shared/difficulty-level/difficulty-level.component';
import { ArtemisTestModule } from '../../test.module';

describe('DifficultyLevelComponent', () => {
    let component: DifficultyLevelComponent;
    let fixture: ComponentFixture<DifficultyLevelComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [DifficultyLevelComponent],
            imports: [NgbTooltipModule, ArtemisTestModule],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(DifficultyLevelComponent);
        component = fixture.componentInstance;
        component.coloredDifficultyLevel = {
            label: 'Easy',
            color: ['success', 'body', 'body'],
        };
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display the correct tooltip label', () => {
        const tooltipElement: HTMLElement = fixture.nativeElement.querySelector('[ngbTooltip]');
        expect(tooltipElement.getAttribute('ngbTooltip')).toBe('Easy');
    });

    it('should apply the correct classes for difficulty colors', () => {
        const skillBars: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.skill-bar');
        expect(skillBars).toHaveLength(3);
        expect(skillBars[0].classList).toContain('bg-success');
        expect(skillBars[1].classList).toContain('bg-body');
        expect(skillBars[2].classList).toContain('bg-body');
    });
});
