import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseExerciseCardComponent } from 'app/core/course/manage/course-exercise-card/course-exercise-card.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockDirective, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';

describe('Course Exercise Card Component', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseExerciseCardComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockDirective(NgbCollapse), CourseExerciseCardComponent],
            providers: [MockProvider(CourseManagementService)],
        }).overrideComponent(CourseExerciseCardComponent, {
            remove: { imports: [TranslateDirective] },
            add: { imports: [MockDirective(TranslateDirective)] },
        });
        fixture = TestBed.createComponent(CourseExerciseCardComponent);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(CourseExerciseCardComponent).toBeDefined();
        // TODO: implement some proper client tests
    });
});
