import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UpcomingExamsAndExercisesComponent } from 'app/admin/upcoming-exams-and-exercises/upcoming-exams-and-exercises.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ArtemisTestModule } from '../../test.module';
import { MockExerciseService } from '../../helpers/mocks/service/mock-exercise.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { MockExamManagementService } from '../../helpers/mocks/service/mock-exam-management.service';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';

describe('UpcomingExamsAndExercisesComponent', () => {
    let component: UpcomingExamsAndExercisesComponent;
    let fixture: ComponentFixture<UpcomingExamsAndExercisesComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [UpcomingExamsAndExercisesComponent, TranslatePipeMock, MockRouterLinkDirective],
            providers: [
                { provide: ExerciseService, useClass: MockExerciseService },
                {
                    provide: ExamManagementService,
                    useClass: MockExamManagementService,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UpcomingExamsAndExercisesComponent);
                component = fixture.componentInstance;
            });
    });

    // The admin module is lazy loaded - we therefore need a dummy test to load
    // the module and verify that there are no dependency related issues.
    it('should render a component from the admin module', () => {
        expect(component).toBeDefined();
    });

    describe('onInit', () => {
        it('should call load exercises and exams on init', () => {
            // WHEN
            component.ngOnInit();

            // THEN
            expect(component.upcomingExercises).toHaveLength(2);
        });
    });
});
