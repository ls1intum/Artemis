import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { FileUploadExerciseDetailComponent } from 'app/exercises/file-upload/manage/file-upload-exercise-detail.component';
import { MockFileUploadExerciseService, fileUploadExercise } from '../../helpers/mocks/service/mock-file-upload-exercise.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AlertService } from 'app/core/util/alert.service';
import { RouterTestingModule } from '@angular/router/testing';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { Course } from 'app/entities/course.model';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ExerciseDetailStatisticsComponent } from 'app/exercises/shared/statistics/exercise-detail-statistics.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { DetailOverviewListComponent } from 'app/detail-overview-list/detail-overview-list.component';

describe('FileUploadExercise Management Detail Component', () => {
    let comp: FileUploadExerciseDetailComponent;
    let fixture: ComponentFixture<FileUploadExerciseDetailComponent>;
    let service: FileUploadExerciseService;
    let statisticsService: StatisticsService;

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
    let statisticsServiceStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule],
            declarations: [
                FileUploadExerciseDetailComponent,
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(NonProgrammingExerciseDetailCommonActionsComponent),
                MockComponent(ExerciseDetailStatisticsComponent),
                MockComponent(DetailOverviewListComponent),
                MockComponent(DocumentationButtonComponent),
            ],
            providers: [
                JhiLanguageHelper,
                AlertService,
                { provide: ActivatedRoute, useValue: route },
                { provide: FileUploadExerciseService, useClass: MockFileUploadExerciseService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(FileUploadExerciseService);
        statisticsService = fixture.debugElement.injector.get(StatisticsService);
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
        comp.ngOnInit();
        tick();

        expect(comp.fileUploadExercise).toEqual(fileUploadExerciseWithCourse);

        fixture.detectChanges();

        expect(comp.exerciseDetailSections).toBeDefined();
    }));

    describe('onInit with course exercise', () => {
        beforeEach(() => {
            route.params = of({ exerciseId: fileUploadExerciseWithCourse.id });
        });

        it('should call load on init and be not in exam mode', () => {
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
            fixture.detectChanges();
            comp.ngOnInit();

            // THEN
            expect(statisticsServiceStub).toHaveBeenCalledTimes(2);
            expect(comp.doughnutStats.participationsInPercent).toBe(100);
            expect(comp.doughnutStats.resolvedPostsInPercent).toBe(50);
            expect(comp.doughnutStats.absoluteAveragePoints).toBe(5);
            expect(comp.isExamExercise).toBeFalse();
            expect(comp.fileUploadExercise).toEqual(fileUploadExerciseWithCourse);
        });
    });

    describe('onInit with exam exercise', () => {
        const fileUploadExerciseWithExerciseGroup: FileUploadExercise = new FileUploadExercise(undefined, new ExerciseGroup());
        fileUploadExerciseWithExerciseGroup.id = 123;

        beforeEach(() => {
            route.params = of({ exerciseId: fileUploadExerciseWithExerciseGroup.id });
        });

        it('should call load on init and be in exam mode', () => {
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
            comp.ngOnInit();

            // THEN
            expect(statisticsServiceStub).toHaveBeenCalledOnce();
            expect(comp.doughnutStats.participationsInPercent).toBe(100);
            expect(comp.doughnutStats.resolvedPostsInPercent).toBe(50);
            expect(comp.doughnutStats.absoluteAveragePoints).toBe(5);
            expect(comp.isExamExercise).toBeTrue();
            expect(comp.fileUploadExercise).toEqual(fileUploadExerciseWithExerciseGroup);
        });
    });
});
