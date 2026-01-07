import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { GradingKeyOverviewComponent } from 'app/assessment/manage/grading/grading-key-overview/grading-key-overview.component';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { BonusService } from 'app/assessment/manage/grading/bonus/bonus.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { ScoresStorageService } from 'app/core/course/manage/course-scores/scores-storage.service';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { GradingKeyTableComponent } from 'app/assessment/manage/grading/grading-key/grading-key-table.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('GradingKeyOverviewComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<GradingKeyOverviewComponent>;
    let component: GradingKeyOverviewComponent;
    let route: ActivatedRoute;

    const studentGrade = '2.0';

    beforeEach(() => {
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

        return TestBed.configureTestingModule({
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
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(GradingKeyOverviewComponent);
                component = fixture.componentInstance;
            });
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
        component.isExam = examId !== undefined;

        component.previousState();

        expect(navUtilServiceSpy).toHaveBeenCalledTimes(1);

        if (examId == undefined) {
            expect(navUtilServiceSpy).toHaveBeenCalledWith(['courses', courseId.toString(), 'statistics']);
        } else {
            expect(navUtilServiceSpy).toHaveBeenCalledWith(['courses', courseId.toString(), 'exams', examId.toString()]);
        }
    });
});
