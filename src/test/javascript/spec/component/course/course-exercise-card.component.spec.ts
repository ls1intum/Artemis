import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseExerciseCardComponent } from 'app/core/course/manage/course-exercise-card.component';
import { CourseManagementService } from 'app/core/course/manage/course-management.service';
import { MockDirective, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';

describe('Course Exercise Card Component', () => {
    let fixture: ComponentFixture<CourseExerciseCardComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockDirective(NgbCollapse)],
            declarations: [CourseExerciseCardComponent, MockDirective(TranslateDirective)],
            providers: [MockProvider(CourseManagementService)],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseExerciseCardComponent);
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(CourseExerciseCardComponent).toBeDefined();
        // TODO: implement some proper client tests
    });
});
