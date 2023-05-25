import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { GradingScale } from 'app/entities/grading-scale.model';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { GradingSystemInfoModalComponent } from 'app/grading-system/grading-system-info-modal/grading-system-info-modal.component';
import { NgModel } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { ActivatedRoute, Params } from '@angular/router';
import { GradingSystemComponent } from 'app/grading-system/grading-system.component';
import { BaseGradingSystemComponent } from 'app/grading-system/base-grading-system/base-grading-system.component';
import { RouterTestingModule } from '@angular/router/testing';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';

describe('Grading System Component', () => {
    let comp: GradingSystemComponent;
    let fixture: ComponentFixture<GradingSystemComponent>;

    const routeParamsSubject = new BehaviorSubject<Params>({ courseId: 1 });
    const route = { params: routeParamsSubject.asObservable() } as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule],
            declarations: [
                GradingSystemComponent,
                MockDirective(NgModel),
                MockComponent(DocumentationButtonComponent),
                MockComponent(GradingSystemInfoModalComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [{ provide: ActivatedRoute, useValue: route }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(GradingSystemComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize for course', () => {
        const courseId = 1;
        routeParamsSubject.next({ courseId });
        const paramsSpy = jest.spyOn(route.params, 'subscribe');

        fixture.detectChanges();

        expect(paramsSpy).toHaveBeenCalledOnce();
        expect(comp.courseId).toBe(courseId);
        expect(comp.examId).toBeUndefined();
        expect(comp.isExam).toBeFalse();
    });

    it('should initialize for exam', () => {
        const courseId = 1;
        const examId = 2;
        routeParamsSubject.next({ courseId, examId });
        const paramsSpy = jest.spyOn(route.params, 'subscribe');

        fixture.detectChanges();

        expect(paramsSpy).toHaveBeenCalledOnce();
        expect(comp.courseId).toBe(courseId);
        expect(comp.examId).toBe(examId);
        expect(comp.isExam).toBeTrue();
    });

    it('should store a reference to child component', () => {
        expect(comp.childComponent).toBeUndefined();

        const childComponent = { gradingScale: new GradingScale() } as BaseGradingSystemComponent;
        comp.onChildActivate(childComponent);
        expect(comp.childComponent).toEqual(childComponent);
    });
});
