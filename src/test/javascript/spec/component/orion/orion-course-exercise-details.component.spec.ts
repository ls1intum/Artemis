import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrionCourseExerciseDetailsComponent } from 'app/orion/participation/orion-course-exercise-details.component';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { MockComponent } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { OrionExerciseDetailsStudentActionsComponent } from 'app/orion/participation/orion-exercise-details-student-actions.component';

describe('OrionCourseExerciseDetailsComponent', () => {
    let fixture: ComponentFixture<OrionCourseExerciseDetailsComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [OrionCourseExerciseDetailsComponent, MockComponent(CourseExerciseDetailsComponent), MockComponent(OrionExerciseDetailsStudentActionsComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(OrionCourseExerciseDetailsComponent);
            });
    });

    it('should contain CourseExerciseDetailsComponent', () => {
        const courseExerciseDetails = fixture.debugElement.query(By.directive(CourseExerciseDetailsComponent));

        expect(courseExerciseDetails).not.toBeNull();
    });
});
