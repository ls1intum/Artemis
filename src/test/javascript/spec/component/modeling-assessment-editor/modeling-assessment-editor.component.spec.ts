import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';

import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { mockedActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { ActivatedRoute, convertToParamMap, ParamMap } from '@angular/router';
import { Mutable } from '../../helpers/mutable';
import { BehaviorSubject } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { AssessmentHeaderComponent } from 'app/assessment/assessment-header/assessment-header.component';
import { Course } from 'app/entities/course.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingAssessmentEditorComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.component';
import { ArtemisModelingAssessmentEditorModule } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.module';

describe('ModelingAssessmentEditorComponent', () => {
    let component: ModelingAssessmentEditorComponent;
    let fixture: ComponentFixture<ModelingAssessmentEditorComponent>;
    let mockAuth: MockAccountService;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule, TranslateModule.forRoot(), ArtemisTestModule, ArtemisModelingAssessmentEditorModule],
            declarations: [],
            providers: [JhiLanguageHelper, mockedActivatedRoute({}, { showBackButton: 'false' })],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ModelingAssessmentEditorComponent);
        component = fixture.componentInstance;
        mockAuth = (fixture.debugElement.injector.get(AccountService) as any) as MockAccountService;
        mockAuth.hasAnyAuthorityDirect([]);
        mockAuth.identity();
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show or hide a back button', () => {
        const route = fixture.debugElement.injector.get(ActivatedRoute) as Mutable<ActivatedRoute>;
        const queryParamMap = route.queryParamMap as BehaviorSubject<ParamMap>;
        queryParamMap.next(convertToParamMap({ hideBackButton: 'true' }));
        fixture.detectChanges();
        let assessmentHeaderComponent: AssessmentHeaderComponent = fixture.debugElement.query(By.directive(AssessmentHeaderComponent)).componentInstance;
        expect(assessmentHeaderComponent.hideBackButton).toBeTruthy();

        queryParamMap.next(convertToParamMap({ hideBackButton: undefined }));
        fixture.detectChanges();
        assessmentHeaderComponent = fixture.debugElement.query(By.directive(AssessmentHeaderComponent)).componentInstance;
        expect(assessmentHeaderComponent.hideBackButton).toBeFalsy();
    });

    it('should propagate isAtLeastInstructor', () => {
        const course = new Course();
        course.isAtLeastInstructor = true;
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        mockAuth.isAtLeastInstructorInCourse(course);
        component['checkPermissions']();
        fixture.detectChanges();
        expect(component.isAtLeastInstructor).toBeTruthy();

        let assessmentLayoutComponent: AssessmentHeaderComponent = fixture.debugElement.query(By.directive(AssessmentLayoutComponent)).componentInstance;
        expect(assessmentLayoutComponent.isAtLeastInstructor).toBeTruthy();

        course.isAtLeastInstructor = false;
        mockAuth.isAtLeastInstructorInCourse(course);
        component['checkPermissions']();
        fixture.detectChanges();
        expect(component.isAtLeastInstructor).toBeFalsy();
        assessmentLayoutComponent = fixture.debugElement.query(By.directive(AssessmentLayoutComponent)).componentInstance;
        expect(assessmentLayoutComponent.isAtLeastInstructor).toBeFalsy();
    });
});
