import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse, HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, UrlSegment, convertToParamMap } from '@angular/router';
import { WindFile } from 'app/programming/shared/entities/wind.file';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { Subject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { LOCAL_STORAGE_KEY_IS_SIMPLE_MODE, ProgrammingExerciseUpdateComponent } from 'app/programming/manage/update/programming-exercise-update.component';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import '@angular/localize/init';
import { ProgrammingLanguageFeatureService } from 'app/programming/shared/services/programming-language-feature/programming-language-feature.service';
import { LockRepositoryPolicy, SubmissionPenaltyPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseInformationComponent } from 'app/programming/manage/update/update-components/information/programming-exercise-information.component';
import { ProgrammingExerciseModeComponent } from 'app/programming/manage/update/update-components/mode/programming-exercise-mode.component';
import { ProgrammingExerciseLanguageComponent } from 'app/programming/manage/update/update-components/language/programming-exercise-language.component';
import { ProgrammingExerciseGradingComponent } from 'app/programming/manage/update/update-components/grading/programming-exercise-grading.component';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import * as Utils from 'app/exercise/course-exercises/course-utils';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_THEIA } from 'app/app.constants';
import { APP_NAME_PATTERN_FOR_SWIFT, PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN } from 'app/shared/constants/input.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MockProvider } from 'ng-mocks';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ExerciseUpdatePlagiarismComponent } from 'app/plagiarism/manage/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { ProfileInfo, ProgrammingLanguageFeature } from 'app/core/layouts/profiles/profile-info.model';
import { signal } from '@angular/core';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { ProgrammingExerciseSharingService } from '../services/programming-exercise-sharing.service';

describe('ProgrammingExerciseUpdateComponent', () => {
    const courseId = 1;
    const course = { id: courseId } as Course;
    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: '123' }) },
        url: {
            pipe: () => ({
                subscribe: () => {},
            }),
        },
    } as ActivatedRoute;
    let comp: ProgrammingExerciseUpdateComponent;
    let fixture: ComponentFixture<ProgrammingExerciseUpdateComponent>;
    let programmingExerciseService: ProgrammingExerciseService;
    let courseService: CourseManagementService;
    let exerciseGroupService: ExerciseGroupService;
    let programmingExerciseFeatureService: ProgrammingLanguageFeatureService;
    let alertService: AlertService;
    let profileService: ProfileService;
    let programmingExerciseSharingService: ProgrammingExerciseSharingService;
    let localStorageService: LocalStorageService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BrowserAnimationsModule, FaIconComponent, OwlNativeDateTimeModule],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: ProgrammingExerciseInstructionAnalysisService, useClass: ProgrammingExerciseInstructionAnalysisService },
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(CalendarService),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ProgrammingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        courseService = TestBed.inject(CourseManagementService);
        exerciseGroupService = TestBed.inject(ExerciseGroupService);
        programmingExerciseFeatureService = TestBed.inject(ProgrammingLanguageFeatureService);
        alertService = TestBed.inject(AlertService);
        profileService = TestBed.inject(ProfileService);
        programmingExerciseSharingService = TestBed.inject(ProgrammingExerciseSharingService);
        localStorageService = TestBed.inject(LocalStorageService);

        const programmingLanguageFeature = {
            programmingLanguage: ProgrammingLanguage.JAVA,
            sequentialTestRuns: true,
            staticCodeAnalysis: true,
            plagiarismCheckSupported: true,
            packageNameRequired: true,
            checkoutSolutionRepositoryAllowed: true,
            projectTypes: [ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_MAVEN],
            auxiliaryRepositoriesSupported: true,
        } as ProgrammingLanguageFeature;

        const newProfileInfo = new ProfileInfo();
        newProfileInfo.activeProfiles = [];
        newProfileInfo.activeModuleFeatures = [];
        newProfileInfo.programmingLanguageFeatures = [programmingLanguageFeature];
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(newProfileInfo);

        comp.isSimpleMode.set(false);

        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
        route.data = of({ programmingExercise });
        jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(programmingLanguageFeature);
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
    });

    describe('initializeEditMode', () => {
        it('should set isSimpleMode to true if localStorage has value "true"', () => {
            localStorageService.store<boolean>(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE, true);

            fixture.detectChanges();

            expect(comp.isSimpleMode()).toBeTruthy();
            expect(localStorageService.retrieve<boolean>(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE)).toBeTrue();
        });

        it('should set isSimpleMode to false if localStorage has value "false"', () => {
            localStorageService.store<boolean>(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE, false);

            fixture.detectChanges();

            expect(comp.isSimpleMode()).toBeFalse();
            expect(localStorageService.retrieve<boolean>(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE)).toBeFalse();
        });

        it('should set isSimpleMode to true if not present in local storage', () => {
            localStorageService.remove(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE);

            fixture.detectChanges();

            expect(comp.isSimpleMode()).toBeTrue();
        });
    });

    it('switchEditMode should toggle isSimpleMode and update local storage', () => {
        localStorageService.store<boolean>(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE, true);
        fixture.detectChanges();
        expect(comp.isSimpleMode()).toBeTruthy(); // ensure the assumed initial state isSimpleMode = true holds

        comp.switchEditMode();

        expect(comp.isSimpleMode()).toBeFalsy();
        expect(localStorageService.retrieve<boolean>(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE)).toBeFalse();
    });

    describe('save', () => {
        it('should call update service on save and refresh calendar events for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(new Course(), undefined);
            entity.id = 123;
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            jest.spyOn(programmingExerciseService, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
            const calendarService = TestBed.inject(CalendarService);
            const refreshSpy = jest.spyOn(calendarService, 'reloadEvents');
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.update).toHaveBeenCalledWith(entity, {});
            expect(comp.isSaving).toBeFalse();
            expect(refreshSpy).toHaveBeenCalledOnce();
        }));

        it('should call create service on save and refresh calendar events for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            jest.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(
                of(
                    new HttpResponse({
                        body: {
                            ...entity,
                            id: 2,
                        },
                    }),
                ),
            );
            const calendarService = TestBed.inject(CalendarService);
            const refreshSpy = jest.spyOn(calendarService, 'reloadEvents');
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.automaticSetup).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toBeFalse();
            expect(refreshSpy).toHaveBeenCalledOnce();
        }));

        it('should trim the exercise title before saving', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            entity.title = 'My Exercise   ';
            jest.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(
                of(
                    new HttpResponse({
                        body: {
                            ...entity,
                            id: 1,
                        },
                    }),
                ),
            );
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;

            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.automaticSetup).toHaveBeenCalledWith(entity);
            expect(entity.title).toBe('My Exercise');
        }));

        it('should fail on error', async () => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.id = 1;
            jest.spyOn(programmingExerciseService, 'update').mockReturnValue(
                throwError(
                    () =>
                        new HttpResponse({
                            headers: new HttpHeaders({ 'X-artemisApp-alert': 'error-message' }),
                        }),
                ),
            );
            const alertSpy = jest.spyOn(alertService, 'addAlert');
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            // WHEN
            comp.save();

            // THEN
            expect(comp.isSaving).toBeFalse();
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'error-message',
                disableTranslation: true,
            });
        });

        describe('should set project type in invalid default value is selected', () => {
            beforeEach(() => {
                const programmingExercise = new ProgrammingExercise(undefined, undefined);
                programmingExercise.releaseDate = dayjs(); // We will get a warning if we do not set a release date
                programmingExercise.projectType = ProjectType.PLAIN_GRADLE;
                programmingExercise.course = course;

                comp.programmingExercise = programmingExercise;
                comp.backupExercise = {} as ProgrammingExercise;
                comp.isCreate = true;
                fixture.detectChanges();
            });

            it('should set valid project type in simple mode if default project type (gradle) is not supported', () => {
                comp.isSimpleMode.set(true);
                comp.projectTypes = [ProjectType.PLAIN_MAVEN];
                fixture.detectChanges();

                comp.save();

                fixture.detectChanges();
                expect(comp.programmingExercise.projectType).toBe(ProjectType.PLAIN_MAVEN);
            });

            it('should keep gradle if gradle is supported', fakeAsync(() => {
                comp.isSimpleMode.set(true);
                comp.projectTypes = [ProjectType.PLAIN_MAVEN, ProjectType.PLAIN_GRADLE];
                fixture.detectChanges();

                comp.save();

                fixture.detectChanges();
                expect(comp.programmingExercise.projectType).toBe(ProjectType.PLAIN_GRADLE);
            }));

            it('should keep gradle not in creation modal', fakeAsync(() => {
                comp.isSimpleMode.set(true);
                comp.isCreate = false;
                comp.projectTypes = [ProjectType.PLAIN_MAVEN];
                fixture.detectChanges();

                comp.save();

                fixture.detectChanges();
                expect(comp.programmingExercise.projectType).toBe(ProjectType.PLAIN_GRADLE);
            }));
        });
    });

    describe('exam mode', () => {
        const examId = 1;
        const exerciseGroupId = 1;
        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.id = exerciseGroupId;
        const expectedExamProgrammingExercise = new ProgrammingExercise(undefined, undefined);
        expectedExamProgrammingExercise.exerciseGroup = exerciseGroup;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId, examId, exerciseGroupId });
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });
        });

        it('should be in exam mode after onInit', fakeAsync(() => {
            // GIVEN
            jest.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));
            jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            // WHEN
            comp.ngOnInit();
            tick(); // simulate async

            // THEN
            expect(exerciseGroupService.find).toHaveBeenCalledWith(courseId, examId, exerciseGroupId);
            expect(comp.isSaving).toBeFalse();
            expect(comp.programmingExercise).toStrictEqual(expectedExamProgrammingExercise);
            expect(comp.isExamMode).toBeTrue();
        }));
    });

    describe('course mode', () => {
        const expectedProgrammingExercise = new ProgrammingExercise(undefined, undefined);
        expectedProgrammingExercise.course = course;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });
        });

        it('should not be in exam mode after onInit', fakeAsync(() => {
            // GIVEN
            jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            // WHEN
            comp.ngOnInit();
            tick(); // simulate async

            // THEN
            expect(courseService.find).toHaveBeenCalledWith(courseId);
            expect(comp.isSaving).toBeFalse();
            expect(comp.programmingExercise).toStrictEqual(expectedProgrammingExercise);
            expect(comp.isExamMode).toBeFalse();
        }));
    });

    describe('default programming language', () => {
        it.each([true, false])(
            'should set default programming language',
            fakeAsync((isExamExercise: boolean) => {
                // SETUP
                const route = TestBed.inject(ActivatedRoute);
                route.url = of([{ path: 'new' } as UrlSegment]);
                if (isExamExercise) {
                    const examId = 1;
                    const exerciseGroupId = 1;
                    const exerciseGroup = new ExerciseGroup();
                    exerciseGroup.id = exerciseGroupId;
                    exerciseGroup.exam = { id: examId, course };
                    route.params = of({ courseId, examId, exerciseGroupId });
                    route.data = of({ programmingExercise: new ProgrammingExercise(undefined, exerciseGroup) });
                    jest.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));
                } else {
                    route.params = of({ courseId });
                    route.data = of({ programmingExercise: new ProgrammingExercise(course, undefined) });
                }
                jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
                // GIVEN
                const testProgrammingLanguage = ProgrammingLanguage.SWIFT;
                expect(new ProgrammingExercise(undefined, undefined).programmingLanguage).not.toBe(testProgrammingLanguage);
                course.defaultProgrammingLanguage = testProgrammingLanguage;
                jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(testProgrammingLanguage));

                // WHEN
                comp.ngOnInit();
                tick();

                // THEN
                expect(comp.programmingExercise.programmingLanguage).toBe(testProgrammingLanguage);
            }),
        );
    });

    describe('programming language change and features', () => {
        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });
            jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            jest.spyOn(programmingExerciseFeatureService, 'supportsProgrammingLanguage').mockReturnValue(true);

            const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
        });

        it('should reset sca settings if new programming language does not support sca', fakeAsync(() => {
            comp.ngOnInit();
            fixture.detectChanges();
            tick();

            // Activate sca
            comp.programmingExercise.staticCodeAnalysisEnabled = true;
            tick();

            comp.programmingExercise.maxStaticCodeAnalysisPenalty = 50;
            tick();

            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBeTrue();
            expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBe(50);

            // Switch to another programming language not supporting sca
            comp.onProgrammingLanguageChange(ProgrammingLanguage.HASKELL);
            tick();

            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBeFalse();
            expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBeUndefined();
            expect(comp.programmingExercise.programmingLanguage).toBe(ProgrammingLanguage.HASKELL);
        }));

        it('should activate SCA for Swift', fakeAsync(() => {
            // WHEN
            fixture.detectChanges();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.SWIFT);

            // THEN
            expect(courseService.find).toHaveBeenCalledWith(courseId);
            expect(comp.selectedProgrammingLanguage).toBe(ProgrammingLanguage.SWIFT);
            expect(comp.staticCodeAnalysisAllowed).toBeTrue();
            expect(comp.packageNamePattern).toBe(APP_NAME_PATTERN_FOR_SWIFT);
        }));

        it('should activate SCA for C', fakeAsync(() => {
            // WHEN
            fixture.detectChanges();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.C);
            comp.onProjectTypeChange(ProjectType.GCC);

            // THEN
            expect(courseService.find).toHaveBeenCalledWith(courseId);
            expect(comp.selectedProgrammingLanguage).toBe(ProgrammingLanguage.C);
            expect(comp.selectedProjectType).toBe(ProjectType.GCC);
            expect(comp.staticCodeAnalysisAllowed).toBeTrue();
        }));

        it('should activate SCA for Java', fakeAsync(() => {
            // WHEN
            fixture.detectChanges();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.JAVA);

            // THEN
            expect(comp.selectedProgrammingLanguage).toBe(ProgrammingLanguage.JAVA);
            expect(comp.staticCodeAnalysisAllowed).toBeTrue();
            expect(comp.packageNamePattern).toBe(PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN);
        }));

        it('should deactivate SCA for C (FACT)', fakeAsync(() => {
            // WHEN
            fixture.detectChanges();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.C);
            comp.onProjectTypeChange(ProjectType.FACT);

            // THEN
            expect(comp.selectedProgrammingLanguage).toBe(ProgrammingLanguage.C);
            expect(comp.selectedProjectType).toBe(ProjectType.FACT);
            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBeFalse();
            expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBeUndefined();
        }));

        it('should clear custom build definition on programming language change', fakeAsync(() => {
            // WHEN
            fixture.detectChanges();
            comp.programmingExercise.buildConfig!.buildPlanConfiguration = 'some custom build definition';
            comp.programmingExercise.buildConfig!.windfile = new WindFile();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.C);
            comp.onProjectTypeChange(ProjectType.FACT);

            // THEN
            expect(comp.programmingExercise.buildConfig?.buildPlanConfiguration).toBeUndefined();
            expect(comp.programmingExercise.buildConfig?.windfile).toBeUndefined();
        }));
    });

    describe('import with static code analysis', () => {
        let route: ActivatedRoute;

        beforeEach(() => {
            jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
        });

        it('should correctly import into an exam exercise', fakeAsync(() => {
            const examId = 1;
            const exerciseGroupId = 1;
            const exerciseGroup = new ExerciseGroup();
            exerciseGroup.id = exerciseGroupId;
            exerciseGroup.exam = { id: examId, course };
            const programmingExercise = new ProgrammingExercise(undefined, exerciseGroup);
            jest.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));
            route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId, examId, exerciseGroupId });
            route.data = of({ programmingExercise });

            comp.ngOnInit();
            tick();
            expect(comp.programmingExercise.exerciseGroup).toBe(exerciseGroup);
            expect(comp.programmingExercise.course).toBeUndefined();
            expect(comp.isImportFromExistingExercise).toBeTrue();
            expect(comp.isExamMode).toBeTrue();
        }));

        it('should reset dates, id and project key', fakeAsync(() => {
            const programmingExercise = getProgrammingExerciseForImport();

            route.data = of({ programmingExercise });
            const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            comp.ngOnInit();
            fixture.detectChanges();
            tick();
            expect(comp.isImportFromFile).toBeFalse();
            expect(comp.isImportFromExistingExercise).toBeTrue();

            verifyImport(programmingExercise);
        }));

        it.each([
            [true, 80, ProjectType.PLAIN_MAVEN],
            [false, undefined, ProjectType.PLAIN_GRADLE],
        ])(
            'should activate recreate build plans and update template when sca changes',
            fakeAsync((scaActivatedOriginal: boolean, maxPenalty: number | undefined, projectType: ProjectType) => {
                const newMaxPenalty = 50;
                const programmingExercise = new ProgrammingExercise(undefined, undefined);
                programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
                programmingExercise.projectType = projectType;
                programmingExercise.staticCodeAnalysisEnabled = scaActivatedOriginal;
                programmingExercise.maxStaticCodeAnalysisPenalty = maxPenalty;
                route.data = of({ programmingExercise });
                comp.ngOnInit();
                fixture.detectChanges();
                tick();

                expect(comp.isImportFromExistingExercise).toBeTrue();
                expect(comp.originalStaticCodeAnalysisEnabled).toBe(scaActivatedOriginal);
                expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(scaActivatedOriginal);
                expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBe(maxPenalty);
                expect(comp.programmingExercise).toBe(programmingExercise);
                expect(courseService.find).toHaveBeenCalledWith(courseId);

                // Only available for Maven
                if (projectType === ProjectType.PLAIN_MAVEN) {
                    // Needed to trigger setting of update template since we can't use UI components.
                    comp.programmingExercise.staticCodeAnalysisEnabled = !scaActivatedOriginal;
                    comp.onStaticCodeAnalysisChanged();

                    expect(comp.importOptions.updateTemplate).toBeTrue();

                    comp.programmingExercise.staticCodeAnalysisEnabled = !scaActivatedOriginal;
                    comp.onStaticCodeAnalysisChanged();
                }

                comp.programmingExercise.staticCodeAnalysisEnabled = !scaActivatedOriginal;
                comp.onStaticCodeAnalysisChanged();

                if (!scaActivatedOriginal) {
                    comp.programmingExercise.maxStaticCodeAnalysisPenalty = newMaxPenalty;
                }

                // Recreate build plan and template update should be automatically selected
                expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(!scaActivatedOriginal);
                expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBe(scaActivatedOriginal ? undefined : newMaxPenalty);
                expect(comp.importOptions.recreateBuildPlans).toBeTrue();
                expect(comp.importOptions.updateTemplate).toBeTrue();

                comp.importOptions.recreateBuildPlans = !comp.importOptions.recreateBuildPlans;
                comp.onRecreateBuildPlanOrUpdateTemplateChange();
                tick();

                // SCA should revert to the state of the original exercise, maxPenalty will revert to undefined
                expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(comp.originalStaticCodeAnalysisEnabled);
                expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBeUndefined();
            }),
        );

        it('should load exercise categories on import', () => {
            const programmingExercise = getProgrammingExerciseForImport();
            route.data = of({ programmingExercise });
            const loadExerciseCategoriesSpy = jest.spyOn(Utils, 'loadCourseExerciseCategories');

            comp.ngOnInit();

            expect(loadExerciseCategoriesSpy).toHaveBeenCalledOnce();
        });
    });

    describe('import from file', () => {
        let route: ActivatedRoute;

        beforeEach(() => {
            route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });

            route.url = of([{ path: 'import-from-file' } as UrlSegment]);
        });

        it('should reset dates, id, project key and store zipFile', fakeAsync(() => {
            const programmingExercise = getProgrammingExerciseForImport();

            route.data = of({ programmingExercise });
            const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            comp.ngOnInit();
            fixture.detectChanges();
            tick();
            expect(comp.isImportFromFile).toBeTrue();
            expect(comp.isImportFromExistingExercise).toBeFalse();

            verifyImport(programmingExercise);
        }));

        it('should call import-from-file from service on import for entity from file', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.zipFileForImport = new File([''], 'test.zip');
            comp.isImportFromFile = true;
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            jest.spyOn(programmingExerciseService, 'importFromFile').mockReturnValue(
                of(
                    new HttpResponse({
                        body: {
                            ...entity,
                            id: 2,
                        },
                    }),
                ),
            );
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            comp.courseId = course.id!;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.importFromFile).toHaveBeenCalledWith(entity, 1);
            expect(comp.isSaving).toBeFalse();
        }));
    });

    describe('import from sharing', () => {
        let route: ActivatedRoute;

        beforeEach(() => {
            route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });

            route.url = of([{ path: 'import-from-sharing' } as UrlSegment]);
            route.queryParams = of({
                basketToken: 'test-basket-token',
                returnURL: '/test-return-url',
                apiBaseURL: 'https://api.example.com',
                selectedExercise: 5,
                checksum: 'test-checksum',
            });
        });

        it('should import and reset dates, id, project key and handle exercise', fakeAsync(() => {
            const programmingExercise = getProgrammingExerciseForImport();

            route.data = of({ programmingExercise });
            const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            const sharingServiceStub = jest.spyOn(programmingExerciseSharingService, 'loadDetailsForExercises');
            sharingServiceStub.mockReturnValue(of(programmingExercise));
            comp.ngOnInit();

            fixture.detectChanges();
            tick();
            expect(comp.isImportFromFile).toBeFalse();
            expect(comp.isImportFromSharing).toBeTrue();
            expect(comp.isImportFromExistingExercise).toBeFalse();

            verifyImport(programmingExercise);
        }));

        it('should also handle exam mode import', fakeAsync(() => {
            const examId = 1;
            const exerciseGroupId = 1;
            const exerciseGroup = new ExerciseGroup();
            exerciseGroup.id = exerciseGroupId;
            exerciseGroup.exam = { id: examId, course };
            route.params = of({ courseId, examId, exerciseGroupId });

            const programmingExercise = getProgrammingExerciseForImport();

            route.data = of({ programmingExercise });
            const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            const sharingServiceStub = jest.spyOn(programmingExerciseSharingService, 'loadDetailsForExercises');
            sharingServiceStub.mockReturnValue(of(programmingExercise));
            comp.ngOnInit();

            fixture.detectChanges();
            tick();
            expect(comp.isImportFromFile).toBeFalse();
            expect(comp.isImportFromSharing).toBeTrue();
            expect(comp.isImportFromExistingExercise).toBeFalse();

            verifyImport(programmingExercise);
        }));

        it('should call create service on save for new sharing entity', fakeAsync(() => {
            // GIVEN
            const entity: ProgrammingExercise = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            jest.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(
                of(
                    new HttpResponse({
                        body: {
                            ...entity,
                            id: 2,
                        },
                    }),
                ),
            );
            jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            jest.spyOn(programmingExerciseSharingService, 'setUpFromSharingImport').mockReturnValue(of(new HttpResponse({ body: entity })));

            comp.isImportFromSharing = true;

            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            comp.courseId = course.id!;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseSharingService.setUpFromSharingImport).toHaveBeenCalledWith(comp.programmingExercise, course, comp['sharingInfo']);
        }));

        it('should call create service on save for new sharing entity, but save failed', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date

            jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            jest.spyOn(programmingExerciseSharingService, 'setUpFromSharingImport').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'save failed', status: 503 })));

            comp.isImportFromSharing = true;

            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            comp.courseId = course.id!;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseSharingService.setUpFromSharingImport).toHaveBeenCalledWith(comp.programmingExercise, course, comp['sharingInfo']);
        }));

        it('should import without buildConfig and reset dates, id, project key and store zipFile', fakeAsync(() => {
            const programmingExercise = getProgrammingExerciseForImport();
            programmingExercise.buildConfig = undefined; // Simulate no build config

            route.data = of({ programmingExercise });
            const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            const sharingServiceStub = jest.spyOn(programmingExerciseSharingService, 'loadDetailsForExercises');
            sharingServiceStub.mockReturnValue(of(programmingExercise));
            comp.ngOnInit();

            fixture.detectChanges();
            tick();
            expect(comp.isImportFromFile).toBeFalse();
            expect(comp.isImportFromSharing).toBeTrue();
            expect(comp.isImportFromExistingExercise).toBeFalse();

            verifyImport(programmingExercise);
        }));
    });

    describe('input error validation', () => {
        beforeEach(() => {
            // GIVEN
            const entity = new ProgrammingExercise(new Course(), undefined);
            entity.id = 123;
            comp.programmingExercise = entity;
            comp.programmingExercise.course = course;
        });

        it('find validation errors for undefined input values', () => {
            // invalid input
            comp.programmingExercise.title = undefined;
            comp.programmingExercise.shortName = undefined;
            comp.programmingExercise.maxPoints = undefined;
            comp.programmingExercise.bonusPoints = undefined;
            comp.programmingExercise.packageName = undefined;

            const reasons: any[] = comp.getInvalidReasons();
            expect(reasons).toHaveLength(5);
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.title.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.shortName.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.points.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.bonusPoints.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('find validation errors for empty input strings', () => {
            // invalid input
            comp.programmingExercise.title = '';
            comp.programmingExercise.shortName = '';
            comp.programmingExercise.packageName = '';

            const reasons: any[] = comp.getInvalidReasons();
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.title.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.shortName.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('find validation errors for input values not matching the pattern', () => {
            comp.programmingExercise.title = '%§"$"§';
            comp.programmingExercise.shortName = '123';
            comp.programmingExercise.maxPoints = 0;
            comp.programmingExercise.bonusPoints = -1;
            comp.programmingExercise.staticCodeAnalysisEnabled = true;
            comp.programmingExercise.maxStaticCodeAnalysisPenalty = -1;

            const reasons: any[] = comp.getInvalidReasons();
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.title.pattern',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.shortName.pattern',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.points.customMin',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.bonusPoints.customMin',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.maxPenalty.pattern',
                translateValues: {},
            });
        });

        it('find validation errors for package name not matching the pattern', () => {
            comp.programmingExercise.packageName = 'de.tum.in';
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.SWIFT;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.pattern.SWIFT',
                translateValues: {},
            });

            comp.programmingExercise.packageName = 'de/';
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.pattern.JAVA',
                translateValues: {},
            });

            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.KOTLIN;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.pattern.KOTLIN',
                translateValues: {},
            });
        });

        it('validateExerciseChannelName', () => {
            comp.programmingExercise.channelName = '';
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.channelName.empty',
                translateValues: {},
            });
        });

        it('validateExerciseShortName', () => {
            comp.programmingExercise.shortName = 'ab';
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.shortName.minlength',
                translateValues: {},
            });
        });

        it('validateExercisePoints', () => {
            comp.programmingExercise.maxPoints = 10_000;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.points.customMax',
                translateValues: {},
            });
        });

        it('validateExerciseBonusPoints', () => {
            comp.programmingExercise.maxPoints = 10_000;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.points.customMax',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language C', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.C;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language Empty', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.EMPTY;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language Python', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.PYTHON;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language Assembler', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.ASSEMBLER;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language OCAML', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.OCAML;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language VHDL', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.VHDL;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('find validation errors for invalid auxiliary repositories', () => {
            comp.auxiliaryRepositoriesValid.set(false);
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.auxiliaryRepository.error',
                translateValues: {},
            });
        });

        it.each([
            [
                'find validation errors for invalid ide selection',
                {
                    activeProfiles: [PROFILE_THEIA],
                },
                {
                    translateKey: 'artemisApp.programmingExercise.allowOnlineEditor.alert',
                    translateValues: {},
                },
            ],
            [
                'find validation errors for invalid ide selection without theia profile',
                {
                    activeProfiles: [],
                },
                {
                    translateKey: 'artemisApp.programmingExercise.allowOnlineEditor.alertNoTheia',
                    translateValues: {},
                },
            ],
        ])('%s', (description, profileInfo, expectedException) => {
            const newProfileInfo = new ProfileInfo();
            newProfileInfo.activeProfiles = profileInfo.activeProfiles;
            jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(newProfileInfo);

            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ programmingExercise: comp.programmingExercise });

            jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            comp.programmingExercise.allowOnlineEditor = false;
            comp.programmingExercise.allowOfflineIde = false;
            comp.programmingExercise.allowOnlineIde = false;

            fixture.detectChanges();

            expect(comp.getInvalidReasons()).toContainEqual(expectedException);
        });

        it('find validation errors for invalid online IDE image', () => {
            comp.programmingExercise.allowOnlineIde = true;
            comp.programmingExercise.buildConfig!.theiaImage = undefined;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.theiaImage.alert',
                translateValues: {},
            });
        });

        it('should update AuxiliaryRepository checkout directory', () => {
            const auxiliaryRepository = new AuxiliaryRepository();
            auxiliaryRepository.checkoutDirectory = 'aux';
            auxiliaryRepository.name = 'aux';
            auxiliaryRepository.repositoryUri = 'auxurl';
            comp.programmingExercise.auxiliaryRepositories = [auxiliaryRepository];
            const returned = comp.updateCheckoutDirectory(auxiliaryRepository)('new-value');
            expect(auxiliaryRepository.checkoutDirectory).toBe('new-value');
            expect(returned).toBe('new-value');
        });

        it('should update AuxiliaryRepository name', () => {
            const auxiliaryRepository = new AuxiliaryRepository();
            auxiliaryRepository.checkoutDirectory = 'aux';
            auxiliaryRepository.name = 'aux';
            auxiliaryRepository.repositoryUri = 'auxurl';
            comp.programmingExercise.auxiliaryRepositories = [auxiliaryRepository];
            const returned = comp.updateRepositoryName(auxiliaryRepository)('new-value');
            expect(auxiliaryRepository.name).toBe('new-value');
            expect(returned).toBe('new-value');
        });

        it('should find no validation errors for valid input', () => {
            comp.programmingExercise.title = 'New title';
            comp.programmingExercise.shortName = 'home2';
            comp.programmingExercise.maxPoints = 10;
            comp.programmingExercise.bonusPoints = 0;
            comp.programmingExercise.staticCodeAnalysisEnabled = true;
            comp.programmingExercise.maxStaticCodeAnalysisPenalty = 60;
            comp.programmingExercise.allowOfflineIde = true;
            comp.programmingExercise.allowOnlineEditor = false;
            comp.programmingExercise.allowOnlineIde = false;
            comp.programmingExercise.packageName = 'de.tum.in';
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;

            expect(comp.getInvalidReasons()).toBeEmpty();
        });

        it('should find validation errors for invalid submission limit value', () => {
            comp.programmingExercise.submissionPolicy = new LockRepositoryPolicy();
            comp.programmingExercise.submissionPolicy.submissionLimit = undefined;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitWarning.required',
                translateValues: {},
            });

            const patternViolatingValues = [0, 501, 30.3];
            for (const value of patternViolatingValues) {
                comp.programmingExercise.submissionPolicy.submissionLimit = value;
                expect(comp.getInvalidReasons()).toContainEqual({
                    translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitWarning.pattern',
                    translateValues: {},
                });
            }
        });

        it('should find validation errors invalid submission exceeding penalty', () => {
            comp.programmingExercise.submissionPolicy = new SubmissionPenaltyPolicy();

            comp.programmingExercise.submissionPolicy.exceedingPenalty = undefined;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInputFieldValidationWarning.required',
                translateValues: {},
            });

            comp.programmingExercise.submissionPolicy.exceedingPenalty = 0;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInputFieldValidationWarning.pattern',
                translateValues: {},
            });
        });

        it('should find no package name related validation error for languages that do not need a package name', () => {
            for (const programmingLanguage of [ProgrammingLanguage.C, ProgrammingLanguage.EMPTY, ProgrammingLanguage.PYTHON]) {
                comp.programmingExercise.programmingLanguage = programmingLanguage;
                expect(comp.getInvalidReasons()).not.toContainEqual({
                    translateKey: 'artemisApp.exercise.form.packageName.undefined',
                    translateValues: {},
                });
            }
        });

        it('should find invalid timeout', () => {
            comp.programmingExercise.buildConfig!.timeoutSeconds = -1;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.timeout.alert',
                translateValues: {},
            });

            comp.programmingExercise.buildConfig!.timeoutSeconds = 100;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.programmingExercise.timeout.alert',
                translateValues: {},
            });
        });

        it('should find invalid checkoutpaths', () => {
            comp.programmingExercise.buildConfig!.assignmentCheckoutPath = 'assignment';
            comp.programmingExercise.buildConfig!.testCheckoutPath = 'assignment';
            comp.programmingExercise.buildConfig!.solutionCheckoutPath = 'solution';

            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.checkoutPath.invalid',
                translateValues: {},
            });

            comp.programmingExercise.buildConfig!.testCheckoutPath = 'test';
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.programmingExercise.checkoutPath.invalid',
                translateValues: {},
            });

            comp.programmingExercise.buildConfig!.assignmentCheckoutPath = 'assignment/';
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.checkoutPath.invalid',
                translateValues: {},
            });

            comp.programmingExercise.buildConfig!.assignmentCheckoutPath = 'assignment';
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.programmingExercise.checkoutPath.invalid',
                translateValues: {},
            });
        });
    });

    describe('disable features based on selected language and project type', () => {
        beforeEach(() => {
            const entity = new ProgrammingExercise(new Course(), undefined);
            entity.id = 123;
            entity.channelName = 'notificationText';
            comp.programmingExercise = entity;
            comp.programmingExercise.course = course;
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;

            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'edit' } as UrlSegment]);
            route.data = of({ programmingExercise: comp.programmingExercise });
        });

        describe('should initialize isEdit variable based on url', () => {
            it('should be false if edit is in url', fakeAsync(() => {
                jest.spyOn(comp, 'selectedProgrammingLanguage', 'set').mockImplementation(() => {});

                fixture.detectChanges();
                tick();

                expect(comp.isEdit).toBeTrue();
            }));

            it('should be true if edit is in url', fakeAsync(() => {
                jest.spyOn(comp, 'selectedProgrammingLanguage', 'set').mockImplementation(() => {});
                const route = TestBed.inject(ActivatedRoute);
                route.params = of({ courseId });
                route.url = of([{ path: 'notEdit' } as UrlSegment]);
                route.data = of({ programmingExercise: comp.programmingExercise });

                fixture.detectChanges();
                tick();

                expect(comp.isEdit).toBeFalse();
            }));
        });

        it('should disable checkboxes for certain options of existing exercise', fakeAsync(() => {
            const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));

            fixture.detectChanges();
            tick();

            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBeFalse();
        }));

        it('should disable options for java dejagnu project type and re-enable them after changing back to maven or gradle', fakeAsync(() => {
            const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            comp.selectedProjectType = ProjectType.MAVEN_BLACKBOX;
            expect(comp.sequentialTestRunsAllowed).toBeFalse();

            comp.selectedProjectType = ProjectType.MAVEN_MAVEN;
            expect(comp.sequentialTestRunsAllowed).toBeTrue();

            comp.selectedProjectType = ProjectType.MAVEN_BLACKBOX;
            expect(comp.sequentialTestRunsAllowed).toBeFalse();

            comp.selectedProjectType = ProjectType.GRADLE_GRADLE;
            expect(comp.sequentialTestRunsAllowed).toBeTrue();
        }));
    });

    it('should return the exercise creation config', fakeAsync(() => {
        const route = TestBed.inject(ActivatedRoute);
        route.params = of({ courseId });
        route.url = of([{ path: 'new' } as UrlSegment]);
        route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });

        const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
        getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));

        fixture.detectChanges();
        tick();

        const problemStepInputs = comp.getProgrammingExerciseCreationConfig();
        expect(problemStepInputs).not.toBeNull();
    }));

    it('stores withdependenices when changed', fakeAsync(() => {
        const route = TestBed.inject(ActivatedRoute);
        route.params = of({ courseId });
        route.url = of([{ path: 'new' } as UrlSegment]);
        route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });

        const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
        getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));

        fixture.detectChanges();
        tick();

        expect(comp.withDependencies).toBeFalse();
        comp.onWithDependenciesChanged(true);
        expect(comp.withDependencies).toBeTrue();
    }));

    it('stores updated categories', fakeAsync(() => {
        const route = TestBed.inject(ActivatedRoute);
        route.params = of({ courseId });
        route.url = of([{ path: 'new' } as UrlSegment]);
        route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });

        const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
        getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));

        fixture.detectChanges();
        tick();

        const categories = [new ExerciseCategory(undefined, undefined)];
        expect(comp.exerciseCategories).toBeUndefined();
        comp.updateCategories(categories);
        expect(comp.exerciseCategories).toBe(categories);
    }));

    it('should validate form sections', () => {
        const calculateFormValidSectionsSpy = jest.spyOn(comp, 'calculateFormStatusSections');
        comp.programmingExercise = new ProgrammingExercise(undefined, undefined);
        comp.exerciseInfoComponent = {
            formValidChanges: new Subject(),
            formValid: true,
        } as ProgrammingExerciseInformationComponent;
        comp.exerciseDifficultyComponent = {
            teamConfigComponent: {
                formValidChanges: new Subject(),
                formValid: true,
            },
        } as ProgrammingExerciseModeComponent;
        comp.exerciseLanguageComponent = {
            formValidChanges: new Subject(),
            formValid: true,
        } as ProgrammingExerciseLanguageComponent;
        comp.exerciseGradingComponent = {
            formValidChanges: new Subject(),
            formValid: true,
        } as ProgrammingExerciseGradingComponent;

        (comp as any).exercisePlagiarismComponent = signal<ExerciseUpdatePlagiarismComponent>({
            isFormValid: signal<boolean>(true),
        } as unknown as ExerciseUpdatePlagiarismComponent).asReadonly();

        comp.ngAfterViewInit();
        // we migrate from subscriptions to signals eventually
        expect(comp.inputFieldSubscriptions).toHaveLength(4);
        comp.calculateFormStatusSections();

        for (const section of comp.formStatusSections()) {
            expect(section.valid).toBeTrue();
        }

        comp.exerciseInfoComponent.formValid = false;
        comp.exerciseInfoComponent.formValidChanges.next(false);

        expect(comp.formStatusSections()[0].valid).toBeFalse();

        comp.exerciseLanguageComponent.formValidChanges.next(false);
        comp.exerciseGradingComponent.formValidChanges.next(false);
        comp.exerciseDifficultyComponent.teamConfigComponent.formValidChanges.next(false);

        expect(calculateFormValidSectionsSpy).toHaveBeenCalledTimes(5);

        comp.programmingExercise.allowOfflineIde = false;
        comp.programmingExercise.allowOnlineEditor = false;
        comp.programmingExercise.allowOnlineIde = false;
        comp.calculateFormStatusSections();
        expect(comp.formStatusSections()[1].valid).toBeFalse();
        comp.programmingExercise.allowOnlineEditor = true;
        comp.calculateFormStatusSections();
        expect(comp.formStatusSections()[1].valid).toBeTrue();

        comp.ngOnDestroy();

        for (const subscription of comp.inputFieldSubscriptions) {
            expect(subscription?.closed ?? true).toBeTrue();
        }
    });

    function verifyImport(importedProgrammingExercise: ProgrammingExercise) {
        expect(comp.programmingExercise.projectKey).toBeUndefined();
        expect(comp.programmingExercise.id).toBeUndefined();
        expect(comp.programmingExercise.dueDate).toBeUndefined();
        expect(comp.programmingExercise.releaseDate).toBeUndefined();
        expect(comp.programmingExercise.startDate).toBeUndefined();
        expect(comp.programmingExercise.assessmentDueDate).toBeUndefined();
        expect(comp.programmingExercise.exampleSolutionPublicationDate).toBeUndefined();
        expect(comp.programmingExercise.zipFileForImport?.name).toBe('test.zip');
        expect(comp.programmingExercise.allowComplaintsForAutomaticAssessments).toBeFalse();
        expect(comp.programmingExercise.allowOfflineIde).toBeTrue();
        expect(comp.programmingExercise.allowOnlineEditor).toBeTrue();
        expect(comp.programmingExercise.programmingLanguage).toBe(ProgrammingLanguage.JAVA);
        expect(comp.programmingExercise.projectType).toBe(ProjectType.PLAIN_MAVEN);
        // allow manual feedback requests, Athena feedback modules and complaints for automatic assessments should be set to false because we reset all dates and hence they can only be false
        expect(comp.programmingExercise.allowManualFeedbackRequests).toBeFalse();
        expect(comp.programmingExercise.athenaConfig?.feedbackSuggestionModule).toBeUndefined();
        expect(comp.programmingExercise.athenaConfig?.preliminaryFeedbackModule).toBeUndefined();
        expect(comp.programmingExercise.allowComplaintsForAutomaticAssessments).toBeFalse();
        // name and short name should also be imported
        expect(comp.programmingExercise.title).toEqual(importedProgrammingExercise.title);
        expect(comp.programmingExercise.shortName).toEqual(importedProgrammingExercise.shortName);
    }
});

const getProgrammingExerciseForImport = () => {
    const programmingExercise = new ProgrammingExercise(undefined, undefined);
    programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
    programmingExercise.projectType = ProjectType.PLAIN_MAVEN;
    programmingExercise.dueDate = dayjs();
    programmingExercise.releaseDate = dayjs();
    programmingExercise.startDate = dayjs();
    programmingExercise.projectKey = 'projectKey';

    programmingExercise.assessmentDueDate = dayjs();
    programmingExercise.exampleSolutionPublicationDate = dayjs();
    programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
    programmingExercise.zipFileForImport = new File([''], 'test.zip');
    programmingExercise.allowOfflineIde = true;
    programmingExercise.allowOnlineEditor = true;
    programmingExercise.allowComplaintsForAutomaticAssessments = true;
    programmingExercise.allowManualFeedbackRequests = true;
    programmingExercise.athenaConfig = {
        feedbackSuggestionModule: 'athena-module-1',
        preliminaryFeedbackModule: 'athena-module-2"',
    };

    history.pushState({ programmingExerciseForImportFromFile: programmingExercise }, '');

    programmingExercise.shortName = 'shortName';
    programmingExercise.title = 'title';

    return programmingExercise;
};

const getProgrammingLanguageFeature = (programmingLanguage: ProgrammingLanguage) => {
    switch (programmingLanguage) {
        case ProgrammingLanguage.SWIFT:
            return {
                programmingLanguage: ProgrammingLanguage.SWIFT,
                sequentialTestRuns: false,
                staticCodeAnalysis: true,
                plagiarismCheckSupported: false,
                packageNameRequired: true,
                checkoutSolutionRepositoryAllowed: false,
                projectTypes: [ProjectType.PLAIN, ProjectType.XCODE],
                auxiliaryRepositoriesSupported: true,
            } as ProgrammingLanguageFeature;
        case ProgrammingLanguage.JAVA:
            return {
                programmingLanguage: ProgrammingLanguage.JAVA,
                sequentialTestRuns: true,
                staticCodeAnalysis: true,
                plagiarismCheckSupported: true,
                packageNameRequired: true,
                checkoutSolutionRepositoryAllowed: true,
                projectTypes: [ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_MAVEN],
                auxiliaryRepositoriesSupported: true,
            } as ProgrammingLanguageFeature;
        case ProgrammingLanguage.HASKELL:
            return {
                programmingLanguage: ProgrammingLanguage.HASKELL,
                sequentialTestRuns: false,
                staticCodeAnalysis: false,
                plagiarismCheckSupported: false,
                packageNameRequired: false,
                checkoutSolutionRepositoryAllowed: true,
                auxiliaryRepositoriesSupported: true,
            } as ProgrammingLanguageFeature;
        case ProgrammingLanguage.C:
            return {
                programmingLanguage: ProgrammingLanguage.C,
                sequentialTestRuns: false,
                staticCodeAnalysis: true,
                plagiarismCheckSupported: true,
                packageNameRequired: false,
                checkoutSolutionRepositoryAllowed: true,
                projectTypes: [ProjectType.FACT, ProjectType.GCC],
                auxiliaryRepositoriesSupported: true,
            } as ProgrammingLanguageFeature;
        default:
            throw new Error();
    }
};
