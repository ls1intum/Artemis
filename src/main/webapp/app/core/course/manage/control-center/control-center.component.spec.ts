import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ControlCenterComponent } from './control-center.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisEnabledComponent } from 'app/iris/manage/settings/shared/iris-enabled/iris-enabled.component';
import { MockComponent, MockDirective } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { ComponentRef } from '@angular/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ControlCenterComponent', () => {
    let componentRef: ComponentRef<ControlCenterComponent>;
    let fixture: ComponentFixture<ControlCenterComponent>;
    let course: Course;

    beforeEach(async () => {
        course = { id: 1, isAtLeastInstructor: true } as Course;

        await TestBed.configureTestingModule({
            imports: [MockDirective(TranslateDirective), FaIconComponent],
            declarations: [ControlCenterComponent, MockComponent(HelpIconComponent), MockComponent(IrisEnabledComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ControlCenterComponent);
        componentRef = fixture.componentRef;
        componentRef.setInput('course', course);
        componentRef.setInput('irisEnabled', true);
        fixture.detectChanges();
    });

    it('should display the control center card when iris is enabled and the user is at least an instructor', () => {
        const card = fixture.debugElement.nativeElement.querySelector('.card');
        expect(card).toBeTruthy();
    });

    it('should not display the control center card when iris is disabled', () => {
        componentRef.setInput('irisEnabled', false);
        fixture.detectChanges();
        const card = fixture.debugElement.nativeElement.querySelector('.card');
        expect(card).toBeFalsy();
    });

    it('should display the iris enabled component with correct inputs', () => {
        const irisEnabledComponent = fixture.debugElement.query(By.directive(IrisEnabledComponent));
        expect(irisEnabledComponent).toBeTruthy();
        expect(irisEnabledComponent.componentInstance.course).toEqual(course);
    });

    it('should display the robot icon', () => {
        const faIconComponent = fixture.debugElement.query(By.directive(FaIconComponent));
        expect(faIconComponent).toBeTruthy();
    });
});
