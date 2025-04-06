import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UpcomingExamsAndExercisesComponent } from 'app/core/admin/upcoming-exams-and-exercises/upcoming-exams-and-exercises.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { MockExerciseService } from '../../helpers/mocks/service/mock-exercise.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { MockExamManagementService } from '../../helpers/mocks/service/mock-exam-management.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('UpcomingExamsAndExercisesComponent', () => {
    let component: UpcomingExamsAndExercisesComponent;
    let fixture: ComponentFixture<UpcomingExamsAndExercisesComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [
                { provide: ExerciseService, useClass: MockExerciseService },
                {
                    provide: ExamManagementService,
                    useClass: MockExamManagementService,
                },
                { provide: TranslateService, useClass: MockTranslateService },
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
