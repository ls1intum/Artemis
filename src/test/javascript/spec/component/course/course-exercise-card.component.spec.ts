import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseExerciseCardComponent } from 'app/course/manage/course-exercise-card.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockDirective, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';

describe('Course Exercise Card Component', () => {
    let fixture: ComponentFixture<CourseExerciseCardComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockDirective(NgbCollapse)],
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
