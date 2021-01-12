import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router, RouterModule } from '@angular/router';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslatePipe } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
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
import { ExerciseType } from 'app/entities/exercise.model';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';
import { MockRouter } from '../../../helpers/mocks/service/mock-route.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

describe('Exercise Groups Component', () => {
    const course = new Course();
    course.id = 456;

    const exam = new Exam();
    exam.course = course;
    exam.id = 123;

    const groups: ExerciseGroup[] = [
        {
            id: 0,
            exercises: [
                { id: 3, type: ExerciseType.TEXT },
                { id: 4, type: ExerciseType.PROGRAMMING },
            ],
        } as ExerciseGroup,
        { id: 1 } as ExerciseGroup,
        { id: 2 } as ExerciseGroup,
    ];

    let comp: ExerciseGroupsComponent;
    let fixture: ComponentFixture<ExerciseGroupsComponent>;
    let examManagementService: ExamManagementService;
    let modalService: NgbModal;
    let router: Router;

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
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseGroupsComponent);
        comp = fixture.componentInstance;

        examManagementService = TestBed.inject(ExamManagementService);
        modalService = TestBed.inject(NgbModal);
        router = TestBed.inject(Router);
    });

    // Always initialized and bind before tests
    beforeEach(fakeAsync(() => {
        fixture.detectChanges();
        tick();
    }));

    it('loads the exercise groups', fakeAsync(() => {
        const mockResponse = new HttpResponse<Exam>({ body: exam });

        spyOn(examManagementService, 'find').and.returnValue(of(mockResponse));

        comp.loadExerciseGroups().subscribe((response) => {
            expect(response.body).not.toBeNull();
            expect(response.body!.id).toEqual(exam.id);
        });

        tick();
    }));

    it('opens the import modal', fakeAsync(() => {
        const mockReturnValue = { result: Promise.resolve({ id: 1 } as ProgrammingExercise) };
        spyOn(modalService, 'open').and.returnValue(mockReturnValue);

        comp.openImportModal(groups[0], ExerciseType.PROGRAMMING);
        tick();

        expect(modalService.open).toHaveBeenCalled();
        expect(router.navigate).toHaveBeenCalled();
    }));

    it('moves up an exercise group', () => {
        comp.exerciseGroups = groups.slice();
        const from = 1;
        const to = 0;

        comp.moveUp(from);

        expect(comp.exerciseGroups[to].id).toEqual(groups[from].id);
        expect(comp.exerciseGroups[from].id).toEqual(groups[to].id);
    });

    it('moves down an exercise group', () => {
        comp.exerciseGroups = groups.slice();
        const from = 0;
        const to = 1;

        comp.moveDown(from);

        expect(comp.exerciseGroups[to].id).toEqual(groups[from].id);
        expect(comp.exerciseGroups[from].id).toEqual(groups[to].id);
    });

    it('maps exercise types to exercise groups', () => {
        comp.exerciseGroups = groups.slice();
        const firstGroupId = groups[0].id!;
        const expectedResult = [ExerciseType.TEXT, ExerciseType.PROGRAMMING];

        comp.setupExerciseGroupToExerciseTypesDict();
        const map = comp.exerciseGroupToExerciseTypesDict;

        expect(map).toBeDefined();
        expect(map.size).toEqual(groups.length);
        expect(map.get(firstGroupId)).toEqual(expectedResult);
    });
});
