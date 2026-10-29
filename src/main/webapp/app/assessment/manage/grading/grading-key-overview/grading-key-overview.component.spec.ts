import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { GradeStepsDTO } from 'app/assessment/shared/entities/grade-step.model';
import { examManagementRoutes } from 'app/exam/manage/exam-management.route';
import { examParticipationRoute } from 'app/exam/overview/exam-participation.route';
import { routes as courseStatisticsRoutes } from 'app/course/overview/course-statistics/course-statistics.route';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { GradingKeyOverviewComponent } from 'app/assessment/manage/grading/grading-key-overview/grading-key-overview.component';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { BonusService } from 'app/assessment/manage/grading/bonus/bonus.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { ScoresStorageService } from 'app/course/manage/course-scores/scores-storage.service';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ActivatedRoute, ActivatedRouteSnapshot, Params, Router } from '@angular/router';
import { GradingKeyTableComponent } from 'app/assessment/manage/grading/grading-key/grading-key-table.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('GradingKeyOverviewComponent', () => {
    let fixture: ComponentFixture<GradingKeyOverviewComponent>;
    let component: GradingKeyOverviewComponent;
    let route: ActivatedRoute;

    const studentGrade = '2.0';

    beforeEach(async () => {
        route = {
            snapshot: { params: {} as Params, queryParams: { grade: studentGrade } as Params, data: {} },
            parent: {
                snapshot: { params: {} },
                parent: {
                    snapshot: {
                        params: { courseId: 345, examId: 123 } as Params,
                    },
                },
            },
        } as ActivatedRoute;

        await TestBed.configureTestingModule({
            imports: [GradingKeyOverviewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(GradingService),
                MockProvider(BonusService),
                MockProvider(CourseStorageService),
                MockProvider(ScoresStorageService),
                MockProvider(ArtemisNavigationUtilService),
                MockProvider(ThemeService),
            ],
        })
            .overrideComponent(GradingKeyOverviewComponent, {
                remove: { imports: [GradingKeyTableComponent, FaIconComponent, ArtemisTranslatePipe, TranslateDirective] },
                add: {
                    imports: [MockComponent(GradingKeyTableComponent), MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(GradingKeyOverviewComponent);
        component = fixture.componentInstance;
    });

    it('should initialize component', () => {
        fixture.detectChanges();

        expect(fixture).toBeTruthy();
        expect(component).toBeTruthy();
        expect(component.examId).toBe(123);
        expect(component.courseId).toBe(345);
        expect(component.studentGradeOrBonusPointsOrGradeBonus).toBe(studentGrade);
    });

    it('should print PDF', () => {
        const printSpy = vi.spyOn(TestBed.inject(ThemeService), 'print').mockImplementation(async () => {});

        component.printPDF();

        expect(printSpy).toHaveBeenCalledTimes(1);
    });

    it.each([456, undefined])('should call the back method on the nav util service on previousState for examId %s', (examId) => {
        const navUtilService = TestBed.inject(ArtemisNavigationUtilService);
        const navUtilServiceSpy = vi.spyOn(navUtilService, 'navigateBack');
        const courseId = 213;

        component.courseId = courseId;
        component.examId = examId;
        component.isExam.set(examId !== undefined);

        component.previousState();

        expect(navUtilServiceSpy).toHaveBeenCalledTimes(1);

        if (examId == undefined) {
            expect(navUtilServiceSpy).toHaveBeenCalledWith(['courses', courseId.toString(), 'statistics']);
        } else {
            expect(navUtilServiceSpy).toHaveBeenCalledWith(['courses', courseId.toString(), 'exams', examId.toString()]);
        }
    });
});

describe('GradingKeyOverviewComponent actual route purpose requests', () => {
    let http: HttpTestingController | undefined;

    const managementRoutes = examManagementRoutes.flatMap((route) => route.children ?? []);
    const management = managementRoutes.find((route) => route.path === ':examId/student-exams/:studentExamId/summary/overview/grading-key')!;
    const student = examParticipationRoute.find((route) => route.path === 'overview/grading-key')!;
    const course = courseStatisticsRoutes.find((route) => route.path === 'grading-key')!;
    const steps: GradeStepsDTO = {
        title: 'Published exam',
        gradeType: GradeType.GRADE,
        gradeSteps: [],
        maxPoints: 10,
        plagiarismGrade: '5.0',
        noParticipationGrade: '5.0',
    };

    beforeEach(() => {
        http = undefined;
    });

    afterEach(() => {
        http?.verify();
        vi.restoreAllMocks();
    });

    it.each([
        { name: 'student exam view', config: student, examId: '123', expected: 'api/assessment/courses/345/exams/123/grading-scale/grade-steps' },
        { name: 'instructor management view', config: management, examId: '123', expected: 'api/assessment/courses/345/exams/123/grading-scale' },
        { name: 'student course statistics view', config: course, examId: undefined, expected: 'api/assessment/courses/345/grading-scale/grade-steps' },
    ])('requests the fixed API from the real overview and table in $name', async ({ config, examId, expected }) => {
        const parent = new ActivatedRoute();
        parent.snapshot = new ActivatedRouteSnapshot();
        parent.snapshot.params = { courseId: '345', examId };
        vi.spyOn(parent, 'parent', 'get').mockReturnValue(null);
        const route = new ActivatedRoute();
        route.snapshot = new ActivatedRouteSnapshot();
        route.snapshot.params = {};
        route.snapshot.queryParams = {};
        route.snapshot.data = config.data ?? {};
        vi.spyOn(route, 'parent', 'get').mockReturnValue(parent);
        await TestBed.configureTestingModule({
            imports: [GradingKeyOverviewComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ScoresStorageService),
                MockProvider(ArtemisNavigationUtilService),
                MockProvider(ThemeService),
            ],
        }).compileComponents();
        http = TestBed.inject(HttpTestingController);
        const fixture = TestBed.createComponent(GradingKeyOverviewComponent);
        fixture.detectChanges();
        const request = http.expectOne({ method: 'GET' });
        const actualUrl = request.request.url;
        request.flush(actualUrl.endsWith('/grade-steps') ? steps : { id: 7, gradeSteps: steps });
        fixture.detectChanges();
        expect(actualUrl).toBe(expected);
        const table = fixture.debugElement.query(By.directive(GradingKeyTableComponent)).componentInstance as GradingKeyTableComponent;
        expect(table.title()).toBe('Published exam');
    });

    it('marks both actual instructor mounts explicitly and leaves student mounts fixed', () => {
        const bonus = managementRoutes.find((route) => route.path === ':examId/student-exams/:studentExamId/summary/overview/bonus-grading-key')!;
        expect(management.data?.['gradingKeyPurpose']).toBe('management');
        expect(bonus.data?.['gradingKeyPurpose']).toBe('management');
        expect(student.data?.['gradingKeyPurpose']).toBeUndefined();
        expect(course.data?.['gradingKeyPurpose']).toBeUndefined();
    });
});
