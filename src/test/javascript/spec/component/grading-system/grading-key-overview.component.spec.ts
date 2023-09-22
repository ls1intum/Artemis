import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { BonusService } from 'app/grading-system/bonus/bonus.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { ScoresStorageService } from 'app/course/course-scores/scores-storage.service';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { GradingKeyTableComponent } from 'app/grading-system/grading-key-overview/grading-key/grading-key-table.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ThemeService } from 'app/core/theme/theme.service';
describe('GradingKeyOverviewComponent', () => {
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

        TestBed.configureTestingModule({
            declarations: [
                GradingKeyOverviewComponent,
                MockComponent(GradingKeyTableComponent),
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
                MockPipe(SafeHtmlPipe),
                MockPipe(GradeStepBoundsPipe),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                MockProvider(GradingSystemService),
                MockProvider(BonusService),
                MockProvider(CourseStorageService),
                MockProvider(ScoresStorageService),
                MockProvider(ArtemisNavigationUtilService),
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(GradingKeyOverviewComponent);
                component = fixture.componentInstance;
            });

        fixture = TestBed.createComponent(GradingKeyOverviewComponent);
    });

    it('should initialize component', () => {
        fixture.detectChanges();

        expect(fixture).toBeTruthy();
        expect(component).toBeTruthy();
        expect(component.examId).toBe(123);
        expect(component.courseId).toBe(345);
        expect(component.studentGradeOrBonusPointsOrGradeBonus).toBe(studentGrade);
    });

    it('should print PDF', fakeAsync(() => {
        const printSpy = jest.spyOn(TestBed.inject(ThemeService), 'print').mockImplementation();

        component.printPDF();

        tick();
        expect(printSpy).toHaveBeenCalledOnce();
    }));

    it.each([456, undefined])('should call the back method on the nav util service on previousState for examId %s', (examId) => {
        const navUtilService = TestBed.inject(ArtemisNavigationUtilService);
        const navUtilServiceSpy = jest.spyOn(navUtilService, 'navigateBack');
        const courseId = 213;

        component.courseId = courseId;
        component.examId = examId;
        component.isExam = examId !== undefined;

        component.previousState();

        expect(navUtilServiceSpy).toHaveBeenCalledOnce();

        if (examId == undefined) {
            expect(navUtilServiceSpy).toHaveBeenCalledWith(['courses', courseId.toString(), 'statistics']);
        } else {
            expect(navUtilServiceSpy).toHaveBeenCalledWith(['courses', courseId.toString(), 'exams', examId.toString()]);
        }
    });
});
