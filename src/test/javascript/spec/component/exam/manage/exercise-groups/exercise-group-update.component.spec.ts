import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { EntityResponseType } from 'app/assessment/shared/complaint.service';
import { Course } from 'app/core/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseGroupUpdateComponent } from 'app/exam/manage/exercise-groups/exercise-group-update.component';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { AlertService } from 'app/shared/service/alert.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, throwError } from 'rxjs';
import { MockRouter } from '../../../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../../../helpers/mocks/service/mock-sync-storage.service';
import '@angular/localize/init';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ExerciseGroupUpdateComponent', () => {
    const course = { id: 456 } as Course;
    const exam: Exam = new Exam();
    const exerciseGroup: ExerciseGroup = new ExerciseGroup();
    exam.course = course;
    exam.id = 123;
    exerciseGroup.id = 1;
    exerciseGroup.exam = exam;

    let component: ExerciseGroupUpdateComponent;
    let fixture: ComponentFixture<ExerciseGroupUpdateComponent>;
    let service: ExerciseGroupService;
    const mockRouter = new MockRouter();
    let alertServiceStub: jest.SpyInstance;
    let alertService: AlertService;

    const data = of({ exam, exerciseGroup });
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id, examId: exam.id }) }, data } as any as ActivatedRoute;
    const navigateSpy = jest.spyOn(mockRouter, 'navigate');

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: mockRouter },
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseGroupUpdateComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(ExerciseGroupService);
        alertService = TestBed.inject(AlertService);
    });

    // Always initialized and bind before tests
    beforeEach(fakeAsync(() => {
        fixture.detectChanges();
        tick();
    }));

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should save exercise group', fakeAsync(() => {
        expect(component).not.toBeNull();
        expect(component.exam).toEqual(exam);
        expect(component.exerciseGroup).toEqual(exerciseGroup);

        const responseFakeExerciseGroup = { body: exerciseGroup } as EntityResponseType;
        jest.spyOn(service, 'update').mockReturnValue(of(responseFakeExerciseGroup));

        component.save();

        expect(component.isSaving).toBeFalse();
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', course.id, 'exams', route.snapshot.paramMap.get('examId'), 'exercise-groups']);
        flush();
    }));

    it('should save exercise group without ID', fakeAsync(() => {
        component.exerciseGroup.id = undefined;

        const responseFakeExerciseGroup = { body: exerciseGroup } as EntityResponseType;
        jest.spyOn(service, 'create').mockReturnValue(of(responseFakeExerciseGroup));

        component.save();

        expect(component.isSaving).toBeFalse();
        expect(component.exam).toEqual(exam);
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', course.id, 'exams', route.snapshot.paramMap.get('examId'), 'exercise-groups']);
        flush();
    }));

    it('should fail while saving with ErrorResponse', fakeAsync(() => {
        alertServiceStub = jest.spyOn(alertService, 'error');
        const error = { status: 404 };
        component.exerciseGroup.id = undefined;

        jest.spyOn(service, 'create').mockReturnValue(throwError(() => new HttpErrorResponse(error)));

        component.save();

        expect(component.isSaving).toBeFalse();
        expect(component.exam).toEqual(exam);
        expect(alertServiceStub).toHaveBeenCalledOnce();
        flush();
    }));
});
