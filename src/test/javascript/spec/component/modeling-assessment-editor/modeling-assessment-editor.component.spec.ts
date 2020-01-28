import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisTestModule } from '../../test.module';
import { ArtemisModelingAssessmentEditorModule } from 'app/modeling-assessment-editor/modeling-assessment-editor.module';
import { ModelingAssessmentEditorComponent } from 'app/modeling-assessment-editor';
import { By } from '@angular/platform-browser';
import { mockedActivatedRoute } from '../../helpers/mock-activated-route-query-param-map';
import { ActivatedRoute, ParamMap, convertToParamMap } from '@angular/router';
import { Mutable } from '../../helpers/mutable';
import { BehaviorSubject } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { User } from 'app/core/user/user.model';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mock-account.service';
import { AssessmentLayoutComponent } from 'app/assessment-shared/assessment-layout/assessment-layout.component';
import { AssessmentHeaderComponent } from 'app/assessment-shared/assessment-header/assessment-header.component';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { Course } from 'app/entities/course';

describe('ModelingAssessmentEditorComponent', () => {
    let component: ModelingAssessmentEditorComponent;
    let fixture: ComponentFixture<ModelingAssessmentEditorComponent>;
    let mockAuth: MockAccountService;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule, TranslateModule.forRoot(), ArtemisTestModule, ArtemisModelingAssessmentEditorModule],
            declarations: [],
            providers: [JhiAlertService, JhiLanguageHelper, mockedActivatedRoute({}, { showBackButton: 'false' })],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ModelingAssessmentEditorComponent);
        component = fixture.componentInstance;
        mockAuth = (fixture.debugElement.injector.get(AccountService) as any) as MockAccountService;
        mockAuth.hasAnyAuthorityDirectSpy.and.returnValue(false);
        mockAuth.identitySpy.and.returnValue(Promise.resolve(new User()));
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
        component.modelingExercise = new ModelingExercise('ClassDiagram', new Course());
        mockAuth.isAtLeastInstructorInCourseSpy.and.returnValue(true);
        component['checkPermissions']();
        fixture.detectChanges();
        expect(component.isAtLeastInstructor).toBeTruthy();

        let assessmentLayoutComponent: AssessmentHeaderComponent = fixture.debugElement.query(By.directive(AssessmentLayoutComponent)).componentInstance;
        expect(assessmentLayoutComponent.isAtLeastInstructor).toBeTruthy();

        mockAuth.isAtLeastInstructorInCourseSpy.and.returnValue(false);
        component['checkPermissions']();
        fixture.detectChanges();
        expect(component.isAtLeastInstructor).toBeFalsy();
        assessmentLayoutComponent = fixture.debugElement.query(By.directive(AssessmentLayoutComponent)).componentInstance;
        expect(assessmentLayoutComponent.isAtLeastInstructor).toBeFalsy();
    });
});
