import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { faCheckDouble, faFileUpload, faFont, faKeyboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { Course } from 'app/entities/course.model';
import { ExamInformationDTO } from 'app/entities/exam-information.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroupsComponent } from 'app/exam/manage/exercise-groups/exercise-groups.component';
import { FileUploadExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/file-upload-exercise-cell/file-upload-exercise-group-cell.component';
import { ModelingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/modeling-exercise-cell/modeling-exercise-group-cell.component';
import { ProgrammingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/programming-exercise-cell/programming-exercise-group-cell.component';
import { QuizExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/quiz-exercise-cell/quiz-exercise-group-cell.component';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/exercises/programming/manage/status/programming-exercise-instructor-status.component';
import { ExamExerciseRowButtonsComponent } from 'app/exercises/shared/exam-exercise-row-buttons/exam-exercise-row-buttons.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockRouter } from '../../../../helpers/mocks/mock-router';
import { MockNgbModalService } from '../../../../helpers/mocks/service/mock-ngb-modal.service';
import { ArtemisTestModule } from '../../../../test.module';

describe('Exercise Groups Component', () => {
    const course = new Course();
    course.id = 456;

    const exam = new Exam();
    exam.course = course;
    exam.id = 123;

    let groups: ExerciseGroup[];

    let comp: ExerciseGroupsComponent;
    let fixture: ComponentFixture<ExerciseGroupsComponent>;

    let exerciseGroupService: ExerciseGroupService;
    let examManagementService: ExamManagementService;
    let eventManager: EventManager;
    let modalService: NgbModal;
    let router: Router;
    let alertService: AlertService;

    const data = of({ exam });
    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: course.id, examId: exam.id }) },
        data,
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule],
            declarations: [
                ExerciseGroupsComponent,
                MockComponent(ExamExerciseRowButtonsComponent),
                MockComponent(ProgrammingExerciseInstructorStatusComponent),
                MockDirective(DeleteButtonDirective),
                MockDirective(HasAnyAuthorityDirective),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FileUploadExerciseGroupCellComponent),
                MockComponent(ModelingExerciseGroupCellComponent),
                MockComponent(ProgrammingExerciseGroupCellComponent),
                MockComponent(QuizExerciseGroupCellComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                MockProvider(AlertService),
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseGroupsComponent);
                comp = fixture.componentInstance;

                exerciseGroupService = TestBed.inject(ExerciseGroupService);
                examManagementService = TestBed.inject(ExamManagementService);
                eventManager = TestBed.inject(EventManager);
                modalService = TestBed.inject(NgbModal);
                alertService = TestBed.inject(AlertService);
                router = TestBed.inject(Router);

                groups = [
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
                // Always initialized and bind before tests
                fixture.detectChanges();
            });
    });

    it('loads the exercise groups', fakeAsync(() => {
        const mockResponse = new HttpResponse<Exam>({ body: exam });

        jest.spyOn(examManagementService, 'find').mockReturnValue(of(mockResponse));

        comp.loadExerciseGroups().subscribe((response) => {
            expect(response.body).not.toBeNull();
            expect(response.body!.id).toBe(exam.id);
        });

        tick();
    }));

    it('loads exam information', fakeAsync(() => {
        const latestIndividualEndDate = dayjs();
        const mockResponse = new HttpResponse<ExamInformationDTO>({ body: { latestIndividualEndDate } });

        jest.spyOn(examManagementService, 'getLatestIndividualEndDateOfExam').mockReturnValue(of(mockResponse));

        comp.loadLatestIndividualEndDateOfExam().subscribe((response) => {
            expect(response).not.toBeNull();
            expect(response!.body).not.toBeNull();
            expect(response!.body!.latestIndividualEndDate).toBe(latestIndividualEndDate);
        });

        tick();
    }));

    it('removes an exercise from group', () => {
        comp.exerciseGroups = groups;

        comp.removeExercise(3, 0);

        expect(comp.exerciseGroups[0].exercises).toHaveLength(1);
    });

    it('deletes an exercise group', fakeAsync(() => {
        comp.exerciseGroups = groups;

        jest.spyOn(exerciseGroupService, 'delete').mockReturnValue(of(new HttpResponse<void>()));
        jest.spyOn(eventManager, 'broadcast');

        comp.deleteExerciseGroup(0, {});
        tick();

        expect(exerciseGroupService.delete).toHaveBeenCalledOnce();
        expect(comp.exerciseGroups).toHaveLength(groups.length - 1);
    }));

    it('returns the exercise icon type quiz', () => {
        const icon = faCheckDouble;
        const exercise = { type: ExerciseType.QUIZ } as Exercise;

        expect(comp.exerciseIcon(exercise)).toBe(icon);
    });

    it('returns the exercise icon type file upload', () => {
        const icon = faFileUpload;
        const exercise = { type: ExerciseType.FILE_UPLOAD } as Exercise;

        expect(comp.exerciseIcon(exercise)).toBe(icon);
    });

    it('returns the exercise icon type modeling', () => {
        const icon = faProjectDiagram;
        const exercise = { type: ExerciseType.MODELING } as Exercise;

        expect(comp.exerciseIcon(exercise)).toBe(icon);
    });

    it('returns the exercise icon type programming', () => {
        const icon = faKeyboard;
        const exercise = { type: ExerciseType.PROGRAMMING } as Exercise;

        expect(comp.exerciseIcon(exercise)).toBe(icon);
    });

    it('returns the exercise icon type text', () => {
        const icon = faFont;
        const exercise = { type: ExerciseType.TEXT } as Exercise;

        expect(comp.exerciseIcon(exercise)).toBe(icon);
    });

    it.each([[ExerciseType.PROGRAMMING], [ExerciseType.TEXT], [ExerciseType.MODELING], [ExerciseType.QUIZ], [ExerciseType.FILE_UPLOAD]])(
        'opens the import modal and navigates to import page',
        fakeAsync((exerciseType: ExerciseType) => {
            const mockReturnValue = {
                result: Promise.resolve({ id: 1 } as Exercise),
                componentInstance: {},
            } as NgbModalRef;
            jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);
            jest.spyOn(router, 'navigate');

            comp.openImportModal(groups[0], exerciseType);
            tick();

            expect(modalService.open).toHaveBeenCalledOnce();
            expect(router.navigate).toHaveBeenCalledOnce();
            expect(router.navigate).toHaveBeenCalledWith(['/course-management', 456, 'exams', 123, 'exercise-groups', 0, `${exerciseType}-exercises`, 'import', 1]);
            expect(mockReturnValue.componentInstance.exerciseType).toEqual(exerciseType);
        }),
    );
    it.each([[ExerciseType.PROGRAMMING], [ExerciseType.TEXT], [ExerciseType.MODELING], [ExerciseType.QUIZ], [ExerciseType.FILE_UPLOAD]])(
        'opens the import modal and navigates to import from file page',
        fakeAsync((exerciseType: ExerciseType) => {
            const mockReturnValue = {
                result: Promise.resolve({ id: undefined } as Exercise),
                componentInstance: {},
            } as NgbModalRef;
            jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);
            jest.spyOn(router, 'navigate');

            comp.openImportModal(groups[0], exerciseType);
            tick();
            expect(modalService.open).toHaveBeenCalledOnce();
            expect(router.navigate).toHaveBeenCalledOnce();
            expect(router.navigate).toHaveBeenCalledWith(
                ['/course-management', 456, 'exams', 123, 'exercise-groups', 0, `${exerciseType}-exercises`, 'import-from-file'],
                expect.anything(),
            );
            expect(mockReturnValue.componentInstance.exerciseType).toEqual(exerciseType);
        }),
    );

    it('moves up an exercise group', () => {
        comp.exerciseGroups = groups;
        const from = 1;
        const to = 0;

        const fromId = comp.exerciseGroups[from].id;
        const toId = comp.exerciseGroups[to].id;

        comp.moveUp(from);

        expect(comp.exerciseGroups[to].id).toBe(fromId);
        expect(comp.exerciseGroups[from].id).toBe(toId);
    });

    it('moves down an exercise group', () => {
        comp.exerciseGroups = groups;
        const from = 0;
        const to = 1;

        const fromId = comp.exerciseGroups[from].id;
        const toId = comp.exerciseGroups[to].id;

        comp.moveDown(from);

        expect(comp.exerciseGroups[to].id).toBe(fromId);
        expect(comp.exerciseGroups[from].id).toBe(toId);
    });

    it('maps exercise types to exercise groups', () => {
        comp.exerciseGroups = groups;
        const firstGroupId = groups[0].id!;
        const expectedResult = [ExerciseType.TEXT, ExerciseType.PROGRAMMING];

        comp.setupExerciseGroupToExerciseTypesDict();
        const map = comp.exerciseGroupToExerciseTypesDict;

        expect(map).toBeDefined();
        expect(map.size).toBe(groups.length);
        expect(map.get(firstGroupId)).toEqual(expectedResult);
    });

    it('opens the import modal for exercise groups', fakeAsync(() => {
        const alertSpy = jest.spyOn(alertService, 'success');
        const exerciseGroup = { id: 1 } as ExerciseGroup;
        const mockReturnValue = {
            componentInstance: {
                subsequentExerciseGroupSelection: undefined,
                targetCourseId: undefined,
                targetExamId: undefined,
            },
            result: Promise.resolve([exerciseGroup]),
        } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);
        comp.openExerciseGroupImportModal();
        tick();

        expect(modalService.open).toHaveBeenCalledOnce();
        expect(comp.exerciseGroups).toEqual([exerciseGroup]);
        expect(alertSpy).toHaveBeenCalledOnce();
    }));
});
