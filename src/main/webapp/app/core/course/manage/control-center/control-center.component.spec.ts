import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ControlCenterComponent } from './control-center.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisEnabledComponent } from 'app/iris/manage/settings/shared/iris-enabled/iris-enabled.component';
import { MockComponent, MockDirective } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { ComponentRef } from '@angular/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { DialogService } from 'primeng/dynamicdialog';

describe('ControlCenterComponent', () => {
    setupTestBed({ zoneless: true });

    let componentRef: ComponentRef<ControlCenterComponent>;
    let fixture: ComponentFixture<ControlCenterComponent>;
    let course: Course;

    beforeEach(async () => {
        course = { id: 1, isAtLeastInstructor: true } as Course;

        await TestBed.configureTestingModule({
            imports: [ControlCenterComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(DialogService)],
        })
            .overrideComponent(ControlCenterComponent, {
                set: {
                    imports: [MockDirective(TranslateDirective), MockComponent(IrisLogoComponent), MockComponent(IrisEnabledComponent)],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ControlCenterComponent);
        componentRef = fixture.componentRef;
        componentRef.setInput('course', course);
        componentRef.setInput('irisEnabled', true);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should display the control center card when iris is enabled and the user is at least an instructor', () => {
        const panel = fixture.debugElement.nativeElement.querySelector('.iris-panel');
        expect(panel).toBeTruthy();
    });

    it('should not display the control center card when iris is disabled', () => {
        componentRef.setInput('irisEnabled', false);
        fixture.detectChanges();
        const panel = fixture.debugElement.nativeElement.querySelector('.iris-panel');
        expect(panel).toBeFalsy();
    });

    it('should display the iris enabled component with correct inputs', () => {
        const irisEnabledComponent = fixture.debugElement.query(By.directive(IrisEnabledComponent));
        expect(irisEnabledComponent).toBeTruthy();
        expect(irisEnabledComponent.componentInstance.course).toEqual(course);
    });

    it('should display the iris logo', () => {
        const irisLogoComponent = fixture.debugElement.query(By.directive(IrisLogoComponent));
        expect(irisLogoComponent).toBeTruthy();
    });
});
