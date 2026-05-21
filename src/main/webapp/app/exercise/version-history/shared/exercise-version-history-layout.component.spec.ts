import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { ExerciseVersionHistoryLayoutComponent } from 'app/exercise/version-history/shared/exercise-version-history-layout.component';

describe('ExerciseVersionHistoryLayoutComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseVersionHistoryLayoutComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseVersionHistoryLayoutComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseVersionHistoryLayoutComponent);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render left and right panes', () => {
        expect(fixture.nativeElement.querySelector('.left-pane')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('.right-pane')).toBeTruthy();
    });
});
