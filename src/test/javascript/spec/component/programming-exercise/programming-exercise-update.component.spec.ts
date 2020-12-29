import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, UrlSegment } from '@angular/router';
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
import { ArtemisProgrammingExerciseUpdateModule } from 'app/exercises/programming/manage/update/programming-exercise-update.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';

describe('ProgrammingExercise Management Update Component', () => {
    const programmingLanguageFeature: ProgrammingLanguageFeature = {
        programmingLanguage: ProgrammingLanguage.JAVA,
        sequentialTestRuns: true,
        staticCodeAnalysis: true,
        plagiarismCheckSupported: true,
        packageNameRequired: true,
        checkoutSolutionRepositoryAllowed: true,
        projectTypes: [ProjectType.ECLIPSE, ProjectType.MAVEN],
    };

    let comp: ProgrammingExerciseUpdateComponent;
    let fixture: ComponentFixture<ProgrammingExerciseUpdateComponent>;
    let programmingExerciseService: ProgrammingExerciseService;
    let courseService: CourseManagementService;
    let exerciseGroupService: ExerciseGroupService;
    let programmingExerciseFeatureService: ProgrammingLanguageFeatureService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, BrowserAnimationsModule, ArtemisProgrammingExerciseUpdateModule, FormDateTimePickerModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        programmingExerciseService = fixture.debugElement.injector.get(ProgrammingExerciseService);
        courseService = fixture.debugElement.injector.get(CourseManagementService);
        exerciseGroupService = fixture.debugElement.injector.get(ExerciseGroupService);
        programmingExerciseFeatureService = fixture.debugElement.injector.get(ProgrammingLanguageFeatureService);
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
            spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').and.returnValue(programmingLanguageFeature);

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
            spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').and.returnValue(programmingLanguageFeature);

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

    describe('import with SCA', () => {
        const maxPenalty = 50;
        const courseId = 1;
        const course = new Course();
        course.id = courseId;
        let programmingExercise: ProgrammingExercise;
        let route: ActivatedRoute;

        beforeEach(() => {
            spyOn(courseService, 'find').and.returnValue(of(new HttpResponse({ body: course })));
            spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').and.returnValue(programmingLanguageFeature);

            programmingExercise = new ProgrammingExercise(undefined, undefined);
            programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
            route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
        });

        it('Should activate recreate build plans and update template when sca changes', fakeAsync(() => {
            programmingExercise.staticCodeAnalysisEnabled = false;
            route.data = of({ programmingExercise });
            comp.ngOnInit();
            fixture.detectChanges();
            tick();

            let scaCheckbox = fixture.nativeElement.querySelector('#field_staticCodeAnalysisEnabled');
            let maxPenaltyInput = fixture.nativeElement.querySelector('#field_maxPenalty');
            const recreateBuildPlanCheckbox = fixture.nativeElement.querySelector('#field_recreateBuildPlans');
            const updateTemplateCheckbox = fixture.nativeElement.querySelector('#field_updateTemplateFiles');

            expect(comp.isImport).toBeTruthy();
            expect(comp.originalStaticCodeAnalysisEnabled).toBeFalsy();
            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBeFalsy();
            expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBeFalsy();
            expect(scaCheckbox.checked).toBeFalsy();
            expect(maxPenaltyInput).toBeFalsy();
            expect(recreateBuildPlanCheckbox.checked).toBeFalsy();
            expect(updateTemplateCheckbox.checked).toBeFalsy();
            expect(comp.programmingExercise).toEqual(programmingExercise);
            expect(courseService.find).toHaveBeenCalledWith(courseId);

            scaCheckbox.click();
            scaCheckbox.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            tick();
            scaCheckbox = fixture.nativeElement.querySelector('#field_staticCodeAnalysisEnabled');
            maxPenaltyInput = fixture.nativeElement.querySelector('#field_maxPenalty');
            maxPenaltyInput.value = maxPenalty;
            maxPenaltyInput.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            tick();

            expect(scaCheckbox.checked).toBeTruthy();
            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBeTruthy();
            expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toEqual(maxPenalty);
            expect(comp.recreateBuildPlans).toBeTruthy();
            expect(comp.updateTemplate).toBeTruthy();
        }));
    });
});
