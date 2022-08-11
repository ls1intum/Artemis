import { HttpClientModule, HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { EntityResponseType } from 'app/complaints/complaint.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupUpdateComponent } from 'app/exam/manage/exercise-groups/exercise-group-update.component';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AlertService } from 'app/core/util/alert.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, throwError } from 'rxjs';
import { MockRouter } from '../../../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';

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
            imports: [HttpClientModule, FormsModule],
            declarations: [ExerciseGroupUpdateComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: mockRouter },
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

    it('Should save exercise group', fakeAsync(() => {
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

    it('Should save exercise group without ID', fakeAsync(() => {
        component.exerciseGroup.id = undefined;

        const responseFakeExerciseGroup = { body: exerciseGroup } as EntityResponseType;
        jest.spyOn(service, 'create').mockReturnValue(of(responseFakeExerciseGroup));

        component.save();

        expect(component.isSaving).toBeFalse();
        expect(component.exam).toEqual(exam);
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', course.id, 'exams', route.snapshot.paramMap.get('examId'), 'exercise-groups']);
        flush();
    }));

    it('Should fail while saving with ErrorResponse', fakeAsync(() => {
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
