import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, RouterModule } from '@angular/router';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslatePipe } from '@ngx-translate/core';
import { JhiAlertService } from 'ng-jhipster';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { ExerciseGroupsComponent } from 'app/exam/manage/exercise-groups/exercise-groups.component';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { ExamExerciseRowButtonsComponent } from 'app/exercises/shared/exam-exercise-row-buttons/exam-exercise-row-buttons.component';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/exercises/programming/manage/status/programming-exercise-instructor-status.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { BuildPlanLinkDirective } from 'app/exercises/programming/shared/utils/build-plan-link.directive';
import { ArtemisTestModule } from '../../../test.module';
import { TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';

describe('Exercise Groups Component', () => {
    const course = new Course();
    course.id = 456;

    const exam = new Exam();
    exam.course = course;
    exam.id = 123;

    const groups: ExerciseGroup[] = [{ id: 0 } as ExerciseGroup, { id: 1 } as ExerciseGroup, { id: 2 } as ExerciseGroup];

    let comp: ExerciseGroupsComponent;
    let fixture: ComponentFixture<ExerciseGroupsComponent>;
    let exerciseGroupService: ExerciseGroupService;
    let examManagementService: ExamManagementService;
    let alertService: JhiAlertService;

    const data = of({ exam });
    const route = ({ snapshot: { paramMap: convertToParamMap({ courseId: course.id, examId: exam.id }) }, data } as any) as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule, RouterModule],
            declarations: [
                ExerciseGroupsComponent,
                ExamExerciseRowButtonsComponent,
                ProgrammingExerciseInstructorStatusComponent,
                MockComponent(AlertComponent),
                MockComponent(AlertErrorComponent),
                MockComponent(BuildPlanLinkDirective),
                MockDirective(DeleteButtonDirective),
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(NgbTooltip),
                MockPipe(TranslatePipe),
            ],
            providers: [{ provide: ActivatedRoute, useValue: route }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseGroupsComponent);
        comp = fixture.componentInstance;
        exerciseGroupService = TestBed.inject(ExerciseGroupService);
        examManagementService = TestBed.inject(ExamManagementService);
        alertService = TestBed.inject(JhiAlertService);
    });

    // Always initialized and bind before tests
    beforeEach(fakeAsync(() => {
        fixture.detectChanges();
        tick();
    }));

    it('first test', fakeAsync(() => {
        const mockResponse = new HttpResponse<Exam>({ body: exam });

        spyOn(examManagementService, 'find').and.returnValue(of(mockResponse));

        comp.loadExerciseGroups().subscribe((response) => {
            expect(response.body).not.toBeNull();
            expect(response.body!.id).toEqual(exam.id);
        });

        tick();
    }));

    it('moves up an exercise group', () => {
        comp.exerciseGroups = groups;

        comp.moveUp(1);

        expect(comp.exerciseGroups[0].id).toEqual(1);
        expect(comp.exerciseGroups[1].id).toEqual(0);
    });
});
