import { async, ComponentFixture, TestBed } from '@angular/core/testing';
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
import { AccountService, JhiLanguageHelper, User } from 'app/core';
import { MockAccountService } from '../../helpers/mock-account.service';

describe('ModelingAssessmentEditorComponent', () => {
    let component: ModelingAssessmentEditorComponent;
    let fixture: ComponentFixture<ModelingAssessmentEditorComponent>;
    let mockAuth: MockAccountService;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule, ArtemisTestModule, ArtemisModelingAssessmentEditorModule],
            declarations: [],
            providers: [JhiAlertService, JhiLanguageHelper, mockedActivatedRoute({}, { showBackButton: 'false' })],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ModelingAssessmentEditorComponent);
        component = fixture.componentInstance;
        mockAuth = (fixture.debugElement.injector.get(AccountService) as any) as MockAccountService;
        mockAuth.hasAnyAuthorityDirectSpy.and.returnValue(false);
        mockAuth.setUser(new User());
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show or hide a back button', () => {
        const route = fixture.debugElement.injector.get(ActivatedRoute) as Mutable<ActivatedRoute>;
        const queryParamMap = route.queryParamMap as BehaviorSubject<ParamMap>;
        queryParamMap.next(convertToParamMap({ showBackButton: 'true' }));

        fixture.detectChanges();
        let backButton = fixture.debugElement.query(By.css('fa-icon.back-button'));
        expect(backButton).toBeTruthy();

        queryParamMap.next(convertToParamMap({ showBackButton: undefined }));
        fixture.detectChanges();
        backButton = fixture.debugElement.query(By.css('fa-icon.back-button'));
        expect(backButton).toBeFalsy();
    });
});
