import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { UpcomingExamsAndExercisesComponent } from 'app/admin/upcoming-exams-and-exercises/upcoming-exams-and-exercises.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisAdminModule } from 'app/admin/admin.module';
import { MockExerciseService } from '../../helpers/mocks/service/mock-exercise.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { MockExamManagementService } from '../../helpers/mocks/service/mock-exam-management.service';

describe('UpcomingExamsAndExercisesComponent', () => {
    let component: UpcomingExamsAndExercisesComponent;
    let fixture: ComponentFixture<UpcomingExamsAndExercisesComponent>;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisAdminModule],
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

    describe('OnInit', () => {
        it('Should call load exercises and exams on init', function () {
            // WHEN
            component.ngOnInit();

            // THEN
            expect(component.upcomingExercises.length).toEqual(2);
        });
    });
});
