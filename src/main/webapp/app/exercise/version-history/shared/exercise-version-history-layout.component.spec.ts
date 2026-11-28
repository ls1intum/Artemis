import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { BehaviorSubject } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { ExerciseVersionHistoryLayoutComponent } from 'app/exercise/version-history/shared/exercise-version-history-layout.component';

describe('ExerciseVersionHistoryLayoutComponent', () => {
    let fixture: ComponentFixture<ExerciseVersionHistoryLayoutComponent>;
    let breakpointChanges: BehaviorSubject<BreakpointState>;

    beforeEach(async () => {
        breakpointChanges = new BehaviorSubject<BreakpointState>({ matches: false, breakpoints: {} });
        await TestBed.configureTestingModule({
            imports: [ExerciseVersionHistoryLayoutComponent],
            providers: [{ provide: BreakpointObserver, useValue: { observe: () => breakpointChanges } }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseVersionHistoryLayoutComponent);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('stacks the panes when the viewport becomes narrow', () => {
        expect(fixture.nativeElement.querySelector('[role="separator"]').getAttribute('aria-orientation')).toBe('vertical');
        breakpointChanges.next({ matches: true, breakpoints: {} });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[role="separator"]').getAttribute('aria-orientation')).toBe('horizontal');
        expect(fixture.nativeElement.querySelector('.left-pane')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('.right-pane')).toBeTruthy();
    });

    it('should render left and right panes', () => {
        expect(fixture.nativeElement.querySelector('.left-pane')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('.right-pane')).toBeTruthy();
    });
});
