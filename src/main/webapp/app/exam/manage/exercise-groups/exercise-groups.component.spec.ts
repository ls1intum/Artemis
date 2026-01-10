import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { faCheckDouble, faFileUpload, faFont, faKeyboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AlertService } from 'app/shared/service/alert.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExamInformationDTO } from 'app/exam/shared/entities/exam-information.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroupsComponent } from 'app/exam/manage/exercise-groups/exercise-groups.component';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Subject, of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/programming/manage/status/programming-exercise-instructor-status.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FileUploadExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/file-upload-exercise-cell/file-upload-exercise-group-cell.component';
import { ModelingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/modeling-exercise-cell/modeling-exercise-group-cell.component';
import { ProgrammingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/programming-exercise-cell/programming-exercise-group-cell.component';
import { QuizExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/quiz-exercise-cell/quiz-exercise-group-cell.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExamExerciseRowButtonsComponent } from 'app/exercise/exam-exercise-row-buttons/exam-exercise-row-buttons.component';

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
    let dialogService: DialogService;
    let router: Router;
    let alertService: AlertService;

    const data = of({ exam });
    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: course.id, examId: exam.id }) },
        data,
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ExerciseGroupsComponent],
            declarations: [
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
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                MockProvider(AlertService),
                { provide: DialogService, useClass: MockDialogService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseGroupsComponent);
                comp = fixture.componentInstance;

                exerciseGroupService = TestBed.inject(ExerciseGroupService);
                examManagementService = TestBed.inject(ExamManagementService);
                eventManager = TestBed.inject(EventManager);
                dialogService = TestBed.inject(DialogService);
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
        'opens the import dialog and navigates to import page',
        fakeAsync((exerciseType: ExerciseType) => {
            const onCloseSubject = new Subject<Exercise | undefined>();
            const mockDialogRef = { onClose: onCloseSubject.asObservable() } as DynamicDialogRef;
            jest.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
            jest.spyOn(router, 'navigate');

            comp.openImportModal(groups[0], exerciseType);

            // Simulate dialog closing with result
            onCloseSubject.next({ id: 1 } as Exercise);
            onCloseSubject.complete();
            tick();

            expect(dialogService.open).toHaveBeenCalledOnce();
            expect(router.navigate).toHaveBeenCalledOnce();
            expect(router.navigate).toHaveBeenCalledWith(['/course-management', 456, 'exams', 123, 'exercise-groups', 0, `${exerciseType}-exercises`, 'import', 1]);
        }),
    );
    it.each([[ExerciseType.PROGRAMMING], [ExerciseType.TEXT], [ExerciseType.MODELING], [ExerciseType.QUIZ], [ExerciseType.FILE_UPLOAD]])(
        'opens the import dialog and navigates to import from file page',
        fakeAsync((exerciseType: ExerciseType) => {
            const onCloseSubject = new Subject<Exercise | undefined>();
            const mockDialogRef = { onClose: onCloseSubject.asObservable() } as DynamicDialogRef;
            jest.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
            jest.spyOn(router, 'navigate');

            comp.openImportModal(groups[0], exerciseType);

            // Simulate dialog closing with result (no id means import from file)
            onCloseSubject.next({ id: undefined } as Exercise);
            onCloseSubject.complete();
            tick();

            expect(dialogService.open).toHaveBeenCalledOnce();
            expect(router.navigate).toHaveBeenCalledOnce();
            expect(router.navigate).toHaveBeenCalledWith(
                ['/course-management', 456, 'exams', 123, 'exercise-groups', 0, `${exerciseType}-exercises`, 'import-from-file'],
                expect.anything(),
            );
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

        const onCloseSubject = new Subject<ExerciseGroup[] | undefined>();
        const mockDialogRef = { onClose: onCloseSubject.asObservable() } as DynamicDialogRef;
        jest.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);

        comp.openExerciseGroupImportModal();

        // Simulate dialog closing with result
        onCloseSubject.next([exerciseGroup]);
        onCloseSubject.complete();
        tick();

        expect(dialogService.open).toHaveBeenCalledOnce();
        expect(comp.exerciseGroups).toEqual([exerciseGroup]);
        expect(alertSpy).toHaveBeenCalledOnce();
    }));
});
