import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, UrlSegment } from '@angular/router';
import { stub } from 'sinon';
import { of } from 'rxjs';
import * as moment from 'moment';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/update/programming-exercise-update.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import {
    ProgrammingLanguageFeature,
    ProgrammingLanguageFeatureService,
} from 'app/exercises/programming/shared/service/programming-language-feature/programming-language-feature.service';

describe('ProgrammingExercise Management Update Component', () => {
    let comp: ProgrammingExerciseUpdateComponent;
    let fixture: ComponentFixture<ProgrammingExerciseUpdateComponent>;
    let debugElement: DebugElement;
    let programmingExerciseService: ProgrammingExerciseService;
    let courseService: CourseManagementService;
    let exerciseGroupService: ExerciseGroupService;
    let programmingExerciseFeatureService: ProgrammingLanguageFeatureService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseUpdateComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        })
            .overrideTemplate(ProgrammingExerciseUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        programmingExerciseService = debugElement.injector.get(ProgrammingExerciseService);
        courseService = debugElement.injector.get(CourseManagementService);
        exerciseGroupService = debugElement.injector.get(ExerciseGroupService);
        programmingExerciseFeatureService = debugElement.injector.get(ProgrammingLanguageFeatureService);
    });

    describe('save', () => {
        it('Should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(new Course(), undefined);
            entity.id = 123;
            entity.releaseDate = moment(); // We will get a warning if we do not set a release date
            spyOn(programmingExerciseService, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.programmingExercise = entity;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.update).toHaveBeenCalledWith(entity, {});
            expect(comp.isSaving).toEqual(false);
        }));

        it('Should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = moment(); // We will get a warning if we do not set a release date
            spyOn(programmingExerciseService, 'automaticSetup').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.programmingExercise = entity;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.automaticSetup).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toEqual(false);
        }));

        it('Should trim the exercise title before saving', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = moment(); // We will get a warning if we do not set a release date
            entity.title = 'My Exercise   ';
            spyOn(programmingExerciseService, 'automaticSetup').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.programmingExercise = entity;

            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.automaticSetup).toHaveBeenCalledWith(entity);
            expect(entity.title).toEqual('My Exercise');
        }));
    });

    describe('exam mode', () => {
        const courseId = 1;
        const examId = 1;
        const groupId = 1;
        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.id = groupId;
        const expectedExamProgrammingExercise = new ProgrammingExercise(undefined, undefined);
        expectedExamProgrammingExercise.exerciseGroup = exerciseGroup;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId, examId, groupId });
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });
        });

        it('Should be in exam mode after onInit', fakeAsync(() => {
            // GIVEN
            spyOn(exerciseGroupService, 'find').and.returnValue(of(new HttpResponse({ body: exerciseGroup })));
            const programmingLanguageFeature = getProgrammingLanguageFeature(ProgrammingLanguage.JAVA);
            spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').and.returnValue(of(programmingLanguageFeature));

            // WHEN
            comp.ngOnInit();
            tick(); // simulate async

            // THEN
            expect(exerciseGroupService.find).toHaveBeenCalledWith(courseId, examId, groupId);
            expect(comp.isSaving).toEqual(false);
            expect(comp.programmingExercise).toEqual(expectedExamProgrammingExercise);
            expect(comp.isExamMode).toBeTruthy();
        }));
    });

    describe('course mode', () => {
        const courseId = 1;
        const course = new Course();
        course.id = courseId;
        const expectedProgrammingExercise = new ProgrammingExercise(undefined, undefined);
        expectedProgrammingExercise.course = course;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });
        });

        it('Should not be in exam mode after onInit', fakeAsync(() => {
            // GIVEN
            spyOn(courseService, 'find').and.returnValue(of(new HttpResponse({ body: course })));
            const programmingLanguageFeature: ProgrammingLanguageFeature = getProgrammingLanguageFeature(ProgrammingLanguage.JAVA);
            spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').and.returnValue(of(programmingLanguageFeature));

            // WHEN
            comp.ngOnInit();
            tick(); // simulate async

            // THEN
            expect(courseService.find).toHaveBeenCalledWith(courseId);
            expect(comp.isSaving).toEqual(false);
            expect(comp.programmingExercise).toEqual(expectedProgrammingExercise);
            expect(comp.isExamMode).toBeFalsy();
        }));
    });

    describe('static code analysis', () => {
        const courseId = 2;
        const course = { id: courseId } as Course;
        const expectedProgrammingExercise = new ProgrammingExercise(course, undefined);
        expectedProgrammingExercise.id = 1;
        expectedProgrammingExercise.programmingLanguage = ProgrammingLanguage.SWIFT;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ programmingExercise: expectedProgrammingExercise });
        });

        it('Should activate SCA for Swift', fakeAsync(() => {
            // GIVEN
            spyOn(courseService, 'find').and.returnValue(of(new HttpResponse({ body: course })));
            const programmingLanguageFeature = getProgrammingLanguageFeature(ProgrammingLanguage.SWIFT);
            stub(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').returns(programmingLanguageFeature);

            // WHEN
            fixture.detectChanges();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.SWIFT);

            // THEN
            expect(courseService.find).toHaveBeenCalledWith(courseId);
            expect(comp.programmingExercise).toEqual(expectedProgrammingExercise);
            expect(comp.selectedProgrammingLanguage).toEqual(ProgrammingLanguage.SWIFT);
            expect(comp.staticCodeAnalysisAllowed).toEqual(true);
            expect(comp.packageNamePattern).toEqual(comp.packageNamePatternForSwift);
        }));

        it('Should activate SCA for Java', fakeAsync(() => {
            // GIVEN
            spyOn(courseService, 'find').and.returnValue(of(new HttpResponse({ body: course })));
            const programmingLanguageFeature = getProgrammingLanguageFeature(ProgrammingLanguage.JAVA);
            stub(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').returns(programmingLanguageFeature);

            // WHEN
            fixture.detectChanges();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.JAVA);

            // THEN
            expect(comp.programmingExercise).toEqual(expectedProgrammingExercise);
            expect(comp.selectedProgrammingLanguage).toEqual(ProgrammingLanguage.JAVA);
            expect(comp.staticCodeAnalysisAllowed).toEqual(true);
            expect(comp.packageNamePattern).toEqual(comp.packageNamePatternForJavaKotlin);
        }));
    });
});

const getProgrammingLanguageFeature = (programmingLanguage: ProgrammingLanguage) => {
    if (programmingLanguage === ProgrammingLanguage.SWIFT) {
        return {
            programmingLanguage: ProgrammingLanguage.SWIFT,
            sequentialTestRuns: false,
            staticCodeAnalysis: true,
            plagiarismCheckSupported: false,
            packageNameRequired: true,
            checkoutSolutionRepositoryAllowed: false,
            projectTypes: [],
        } as ProgrammingLanguageFeature;
    } else {
        return {
            programmingLanguage: ProgrammingLanguage.JAVA,
            sequentialTestRuns: true,
            staticCodeAnalysis: true,
            plagiarismCheckSupported: true,
            packageNameRequired: true,
            checkoutSolutionRepositoryAllowed: true,
            projectTypes: [ProjectType.ECLIPSE, ProjectType.MAVEN],
        } as ProgrammingLanguageFeature;
    }
};
