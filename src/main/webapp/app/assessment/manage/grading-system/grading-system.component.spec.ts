import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { BehaviorSubject } from 'rxjs';
import { ActivatedRoute, Params } from '@angular/router';
import { GradingSystemComponent } from 'app/assessment/manage/grading-system/grading-system.component';
import { BaseGradingSystemComponent } from 'app/assessment/manage/grading-system/base-grading-system/base-grading-system.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Grading System Component', () => {
    let comp: GradingSystemComponent;
    let fixture: ComponentFixture<GradingSystemComponent>;

    const routeParamsSubject = new BehaviorSubject<Params>({ courseId: 1 });
    const route = { params: routeParamsSubject.asObservable() } as ActivatedRoute;

    beforeEach(waitForAsync(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(GradingSystemComponent);
        comp = fixture.componentInstance;
    }));

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize for course', () => {
        const courseId = 1;

        fixture.componentRef.setInput('courseId', courseId);
        fixture.detectChanges();

        expect(comp.courseId()).toBe(courseId);
        expect(comp.examId()).toBeUndefined();
        expect(comp.isExam()).toBeFalse();
    });

    it('should initialize for exam', () => {
        const courseId = 1;
        const examId = 2;
        fixture.componentRef.setInput('examId', examId);
        fixture.componentRef.setInput('courseId', courseId);

        fixture.detectChanges();

        // expect(paramsSpy).toHaveBeenCalledOnce();
        expect(comp.courseId()).toBe(courseId);
        expect(comp.examId()).toBe(examId);
        expect(comp.isExam()).toBeTrue();
    });

    it('should store a reference to child component', () => {
        expect(comp.childComponent).toBeUndefined();

        const childComponent = { gradingScale: new GradingScale() } as BaseGradingSystemComponent;
        comp.onChildActivate(childComponent);
        expect(comp.childComponent).toEqual(childComponent);
    });
});
