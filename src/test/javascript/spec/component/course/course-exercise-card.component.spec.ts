import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { CourseExerciseCardComponent } from 'app/course/manage/course-exercise-card.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';

describe('Course Exercise Card Component', () => {
    let fixture: ComponentFixture<CourseExerciseCardComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseExerciseCardComponent, MockDirective(NgbCollapse), MockDirective(TranslateDirective)],
            providers: [MockProvider(CourseManagementService)],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseExerciseCardComponent);
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(CourseExerciseCardComponent).toBeDefined();
    });
});
