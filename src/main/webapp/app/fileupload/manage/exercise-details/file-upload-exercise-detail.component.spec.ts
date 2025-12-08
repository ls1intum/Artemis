import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of } from 'rxjs';
import { FileUploadExerciseDetailComponent } from 'app/fileupload/manage/exercise-details/file-upload-exercise-detail.component';
import { MockFileUploadExerciseService, fileUploadExercise } from 'test/helpers/mocks/service/mock-file-upload-exercise.service';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { AlertService } from 'app/shared/service/alert.service';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercise/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { DetailOverviewListComponent } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('FileUploadExercise Management Detail Component', () => {
    let comp: FileUploadExerciseDetailComponent;
    let fixture: ComponentFixture<FileUploadExerciseDetailComponent>;
    let service: FileUploadExerciseService;
    let statisticsService: StatisticsService;
    let statisticsServiceStub: jest.SpyInstance;

    const route = {
        data: of({ fileUploadExercise }),
        params: of({ exerciseId: 2 }),
    } as any as ActivatedRoute;

    const course: Course = { id: 123 } as Course;
    const fileUploadExerciseWithCourse: FileUploadExercise = new FileUploadExercise(course, undefined);
    fileUploadExerciseWithCourse.id = 123;
    fileUploadExerciseWithCourse.isAtLeastTutor = true;
    fileUploadExerciseWithCourse.isAtLeastEditor = true;
    fileUploadExerciseWithCourse.isAtLeastInstructor = true;

    const fileUploadExerciseStatistics = {
        averageScoreOfExercise: 50,
        maxPointsOfExercise: 10,
        absoluteAveragePoints: 5,
        scoreDistribution: [5, 0, 0, 0, 0, 0, 0, 0, 0, 5],
        numberOfExerciseScores: 10,
        numberOfParticipations: 10,
        numberOfStudentsOrTeamsInCourse: 10,
        participationsInPercent: 100,
        numberOfPosts: 4,
        numberOfResolvedPosts: 2,
        resolvedPostsInPercent: 50,
    } as ExerciseManagementStatisticsDto;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FileUploadExerciseDetailComponent],
            declarations: [
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(NonProgrammingExerciseDetailCommonActionsComponent),
                MockComponent(ExerciseDetailStatisticsComponent),
                MockComponent(DetailOverviewListComponent),
                MockComponent(DocumentationButtonComponent),
            ],
            providers: [
                provideRouter([]),
                JhiLanguageHelper,
                AlertService,
                { provide: ActivatedRoute, useValue: route },
                { provide: FileUploadExerciseService, useClass: MockFileUploadExerciseService },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        service = TestBed.inject(FileUploadExerciseService);
        statisticsService = TestBed.inject(StatisticsService);
        statisticsServiceStub = jest.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(fileUploadExerciseStatistics));
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load all on init', fakeAsync(() => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: fileUploadExerciseWithCourse,
                    headers,
                }),
            ),
        );

        fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
        comp = fixture.componentInstance;
        // Logic runs on creation - trigger effects
        fixture.detectChanges();
        tick(500);

        expect(comp.fileUploadExercise()).toEqual(fileUploadExerciseWithCourse);
        fixture.detectChanges();
        expect(comp.exerciseDetailSections()).toBeDefined();
    }));

    describe('onInit with course exercise', () => {
        beforeEach(() => {
            route.params = of({ exerciseId: fileUploadExerciseWithCourse.id });
        });

        it('should call load on init and be not in exam mode', fakeAsync(() => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            jest.spyOn(service, 'find').mockReturnValue(
                of(
                    new HttpResponse({
                        body: fileUploadExerciseWithCourse,
                        headers,
                    }),
                ),
            );

            // WHEN
            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            comp = fixture.componentInstance;
            fixture.detectChanges();
            tick(500);

            // THEN
            expect(statisticsServiceStub).toHaveBeenCalledOnce();
            expect(comp.doughnutStats()!.participationsInPercent).toBe(100);
            expect(comp.doughnutStats()!.resolvedPostsInPercent).toBe(50);
            expect(comp.doughnutStats()!.absoluteAveragePoints).toBe(5);
            expect(comp.isExamExercise()).toBeFalse();
            expect(comp.fileUploadExercise()).toEqual(fileUploadExerciseWithCourse);
        }));
    });

    describe('onInit with exam exercise', () => {
        const fileUploadExerciseWithExerciseGroup: FileUploadExercise = new FileUploadExercise(undefined, new ExerciseGroup());
        fileUploadExerciseWithExerciseGroup.id = 123;

        beforeEach(() => {
            route.params = of({ exerciseId: fileUploadExerciseWithExerciseGroup.id });
        });

        it('should call load on init and be in exam mode', fakeAsync(() => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            jest.spyOn(service, 'find').mockReturnValue(
                of(
                    new HttpResponse({
                        body: fileUploadExerciseWithExerciseGroup,
                        headers,
                    }),
                ),
            );

            // WHEN
            fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
            comp = fixture.componentInstance;
            fixture.detectChanges();
            tick(500);

            // THEN
            expect(statisticsServiceStub).toHaveBeenCalledOnce();
            expect(comp.doughnutStats()!.participationsInPercent).toBe(100);
            expect(comp.doughnutStats()!.resolvedPostsInPercent).toBe(50);
            expect(comp.doughnutStats()!.absoluteAveragePoints).toBe(5);
            expect(comp.isExamExercise()).toBeTrue();
            expect(comp.fileUploadExercise()).toEqual(fileUploadExerciseWithExerciseGroup);
        }));
    });
});
