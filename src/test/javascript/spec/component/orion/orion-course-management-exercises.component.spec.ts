import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { OrionCourseManagementExercisesComponent } from 'app/orion/management/orion-course-management-exercises.component';
import { CourseManagementExercisesComponent } from 'app/course/manage/course-management-exercises.component';

describe('OrionCourseManagementExercisesComponent', () => {
    let fixture: ComponentFixture<OrionCourseManagementExercisesComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [OrionCourseManagementExercisesComponent, MockComponent(CourseManagementExercisesComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(OrionCourseManagementExercisesComponent);
            });
    });

    it('should contain CourseManagementExercisesComponent', () => {
        const courseExerciseDetails = fixture.debugElement.query(By.directive(CourseManagementExercisesComponent));

        expect(courseExerciseDetails).not.toBeNull();
    });
});
