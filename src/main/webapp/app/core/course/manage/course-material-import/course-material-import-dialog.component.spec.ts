import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseSummaryDTO } from 'app/core/course/shared/entities/course-summary.model';
import { CourseMaterialImportDialogComponent } from './course-material-import-dialog.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseMaterialImportService } from './course-material-import.service';
import { CourseForImportDTOPagingService } from 'app/core/course/shared/services/course-for-import-dto-paging-service';
import { AlertService } from 'app/shared/service/alert.service';
import { of, throwError } from 'rxjs';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { CourseForImportDTO } from 'app/core/course/shared/entities/course.model';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { TableModule } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { PaginatorModule } from 'primeng/paginator';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('CourseMaterialImportDialogComponent', () => {
    let component: CourseMaterialImportDialogComponent;
    let fixture: ComponentFixture<CourseMaterialImportDialogComponent>;
    let importService: CourseMaterialImportService;
    let courseSearchService: CourseForImportDTOPagingService;
    let alertService: AlertService;

    const mockCourses: CourseForImportDTO[] = [
        { id: 1, title: 'Course 1', shortName: 'C1', semester: 'WS2024' },
        { id: 2, title: 'Course 2', shortName: 'C2', semester: 'SS2024' },
        { id: 3, title: 'Course 3', shortName: 'C3', semester: 'WS2023' },
    ];

    const mockSummary: CourseSummaryDTO = {
        numberOfStudents: 100,
        numberOfTutors: 5,
        numberOfEditors: 2,
        numberOfInstructors: 3,
        numberOfParticipations: 500,
        numberOfSubmissions: 1000,
        numberOfResults: 800,
        numberOfConversations: 50,
        numberOfPosts: 200,
        numberOfAnswerPosts: 150,
        numberOfCompetencies: 10,
        numberOfCompetencyProgress: 1000,
        numberOfLearnerProfiles: 100,
        numberOfIrisChatSessions: 25,
        numberOfLLMTraces: 100,
        numberOfBuilds: 500,
        numberOfExams: 2,
        numberOfExercises: 15,
        numberOfProgrammingExercises: 5,
        numberOfTextExercises: 3,
        numberOfModelingExercises: 4,
        numberOfQuizExercises: 2,
        numberOfFileUploadExercises: 1,
        numberOfLectures: 8,
        numberOfFaqs: 12,
        numberOfTutorialGroups: 6,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                CourseMaterialImportDialogComponent,
                FormsModule,
                MockModule(DialogModule),
                MockModule(ButtonModule),
                MockModule(CheckboxModule),
                MockModule(TableModule),
                MockModule(InputTextModule),
                MockModule(ProgressSpinnerModule),
                MockModule(PaginatorModule),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(CourseMaterialImportService),
                MockProvider(CourseForImportDTOPagingService),
                MockProvider(AlertService),
            ],
        })
            .overrideComponent(CourseMaterialImportDialogComponent, {
                set: {
                    imports: [
                        FormsModule,
                        MockModule(DialogModule),
                        MockModule(ButtonModule),
                        MockModule(CheckboxModule),
                        MockModule(TableModule),
                        MockModule(InputTextModule),
                        MockModule(ProgressSpinnerModule),
                        MockModule(PaginatorModule),
                        MockDirective(TranslateDirective),
                        MockPipe(ArtemisTranslatePipe),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(CourseMaterialImportDialogComponent);
        component = fixture.componentInstance;
        importService = TestBed.inject(CourseMaterialImportService);
        courseSearchService = TestBed.inject(CourseForImportDTOPagingService);
        alertService = TestBed.inject(AlertService);

        // Set required input
        fixture.componentRef.setInput('targetCourseId', 999);

        // Initialize component and apply mocks
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('open and close', () => {
        it('should set show to true when open is called', () => {
            expect(component.show()).toBeFalse();
            component.open();
            expect(component.show()).toBeTrue();
        });

        it('should set show to false when close is called', () => {
            component.open();
            expect(component.show()).toBeTrue();
            component.close();
            expect(component.show()).toBeFalse();
        });

        it('should reset state when close is called', () => {
            component.searchTerm.set('search term');
            component.selectedCourse.set(mockCourses[0]);
            component.currentStep.set('selectOptions');

            component.close();

            expect(component.searchTerm()).toBe('');
            expect(component.selectedCourse()).toBeUndefined();
            expect(component.currentStep()).toBe('selectCourse');
        });
    });

    describe('course loading', () => {
        it('should load courses when dialog opens', async () => {
            jest.spyOn(courseSearchService, 'search').mockReturnValue(
                of({
                    resultsOnPage: mockCourses,
                    numberOfPages: 1,
                }),
            );

            component.open();
            // Manually call loadCourses since effect uses untracked
            await component.loadCourses();

            expect(courseSearchService.search).toHaveBeenCalled();
            expect(component.courses()).toHaveLength(3);
        });

        it('should filter out target course from results', async () => {
            const coursesWithTarget = [...mockCourses, { id: 999, title: 'Target Course', shortName: 'TC', semester: 'WS2024' }];
            jest.spyOn(courseSearchService, 'search').mockReturnValue(
                of({
                    resultsOnPage: coursesWithTarget,
                    numberOfPages: 1,
                }),
            );

            component.open();
            await component.loadCourses();

            expect(component.courses()).toHaveLength(3);
            expect(component.courses().find((c) => c.id === 999)).toBeUndefined();
        });

        it('should handle error when loading courses', async () => {
            // Use status 403 since status 500 intentionally doesn't show alerts
            jest.spyOn(courseSearchService, 'search').mockReturnValue(throwError(() => ({ status: 403 })));
            const alertSpy = jest.spyOn(alertService, 'error');

            component.open();
            await component.loadCourses();

            expect(alertSpy).toHaveBeenCalledWith('error.http.403');
        });
    });

    describe('course selection', () => {
        beforeEach(() => {
            jest.spyOn(importService, 'getImportSummary').mockReturnValue(of(mockSummary));
        });

        it('should load summary when course is selected', async () => {
            await component.selectCourse(mockCourses[0]);

            expect(importService.getImportSummary).toHaveBeenCalledWith(999, 1);
            expect(component.sourceSummary()).toEqual(mockSummary);
            expect(component.currentStep()).toBe('selectOptions');
        });

        it('should go back to course selection', async () => {
            await component.selectCourse(mockCourses[0]);

            expect(component.currentStep()).toBe('selectOptions');

            component.backToCourseSelection();

            expect(component.currentStep()).toBe('selectCourse');
            expect(component.selectedCourse()).toBeUndefined();
            expect(component.sourceSummary()).toBeUndefined();
        });
    });

    describe('computed properties', () => {
        beforeEach(async () => {
            jest.spyOn(importService, 'getImportSummary').mockReturnValue(of(mockSummary));
            await component.selectCourse(mockCourses[0]);
        });

        it('should correctly compute hasExercises', () => {
            expect(component.hasExercises()).toBeTrue();
        });

        it('should correctly compute hasLectures', () => {
            expect(component.hasLectures()).toBeTrue();
        });

        it('should correctly compute hasExams', () => {
            expect(component.hasExams()).toBeTrue();
        });

        it('should correctly compute hasCompetencies', () => {
            expect(component.hasCompetencies()).toBeTrue();
        });

        it('should correctly compute hasTutorialGroups', () => {
            expect(component.hasTutorialGroups()).toBeTrue();
        });

        it('should correctly compute hasFaqs', () => {
            expect(component.hasFaqs()).toBeTrue();
        });

        it('should return false for canImport when no options selected', () => {
            expect(component.canImport()).toBeFalse();
        });

        it('should return true for canImport when at least one option is selected', () => {
            component.importExercises.set(true);
            expect(component.canImport()).toBeTrue();
        });
    });

    describe('import options with empty source', () => {
        const emptySummary: CourseSummaryDTO = {
            ...mockSummary,
            numberOfExercises: 0,
            numberOfLectures: 0,
            numberOfExams: 0,
            numberOfCompetencies: 0,
            numberOfTutorialGroups: 0,
            numberOfFaqs: 0,
        };

        beforeEach(async () => {
            jest.spyOn(importService, 'getImportSummary').mockReturnValue(of(emptySummary));
            await component.selectCourse(mockCourses[0]);
        });

        it('should return false for all has* computeds when source is empty', () => {
            expect(component.hasExercises()).toBeFalse();
            expect(component.hasLectures()).toBeFalse();
            expect(component.hasExams()).toBeFalse();
            expect(component.hasCompetencies()).toBeFalse();
            expect(component.hasTutorialGroups()).toBeFalse();
            expect(component.hasFaqs()).toBeFalse();
        });

        it('should return false for canImport even when options are selected if source is empty', () => {
            component.importExercises.set(true);
            component.importLectures.set(true);
            expect(component.canImport()).toBeFalse();
        });
    });

    describe('execute import', () => {
        beforeEach(async () => {
            jest.spyOn(importService, 'getImportSummary').mockReturnValue(of(mockSummary));
            await component.selectCourse(mockCourses[0]);
        });

        it('should call importMaterial service and close dialog on success', async () => {
            jest.spyOn(importService, 'importMaterial').mockReturnValue(
                of({
                    exercisesImported: 15,
                    lecturesImported: 0,
                    examsImported: 0,
                    competenciesImported: 0,
                    tutorialGroupsImported: 0,
                    faqsImported: 0,
                    errors: [],
                }),
            );
            const successSpy = jest.spyOn(alertService, 'success');
            const closeSpy = jest.spyOn(component, 'close');
            const emitSpy = jest.spyOn(component.importStarted, 'emit');

            component.importExercises.set(true);
            await component.executeImport();

            expect(importService.importMaterial).toHaveBeenCalledWith(999, {
                sourceCourseId: 1,
                importExercises: true,
                importLectures: false,
                importExams: false,
                importCompetencies: false,
                importTutorialGroups: false,
                importFaqs: false,
            });
            expect(successSpy).toHaveBeenCalled();
            expect(closeSpy).toHaveBeenCalled();
            expect(emitSpy).toHaveBeenCalled();
        });

        it('should not execute import when canImport is false', async () => {
            jest.spyOn(importService, 'importMaterial');

            // No options selected
            await component.executeImport();

            expect(importService.importMaterial).not.toHaveBeenCalled();
        });

        it('should not execute import when no course is selected', async () => {
            jest.spyOn(importService, 'importMaterial');

            component.selectedCourse.set(undefined);
            component.importExercises.set(true);
            await component.executeImport();

            expect(importService.importMaterial).not.toHaveBeenCalled();
        });
    });

    describe('search and pagination', () => {
        beforeEach(() => {
            jest.spyOn(courseSearchService, 'search').mockReturnValue(
                of({
                    resultsOnPage: mockCourses,
                    numberOfPages: 3,
                }),
            );
        });

        it('should reset page on search change', async () => {
            component.first.set(20);
            component.onSearchChange();
            await fixture.whenStable();

            expect(component.first()).toBe(0);
            expect(courseSearchService.search).toHaveBeenCalled();
        });

        it('should update pagination on page change', async () => {
            component.onPageChange({ first: 10, rows: 10 });
            await fixture.whenStable();

            expect(component.first()).toBe(10);
            expect(component.rows()).toBe(10);
            expect(courseSearchService.search).toHaveBeenCalled();
        });
    });

    describe('header title', () => {
        it('should return selectCourse translation for first step', () => {
            component.currentStep.set('selectCourse');
            expect(component.headerTitle()).toBeDefined();
        });

        it('should return selectOptions translation for second step', () => {
            component.currentStep.set('selectOptions');
            expect(component.headerTitle()).toBeDefined();
        });
    });
});
