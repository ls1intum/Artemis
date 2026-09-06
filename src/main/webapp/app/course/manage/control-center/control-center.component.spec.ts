import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ControlCenterComponent } from './control-center.component';
import { Course } from 'app/course/shared/entities/course.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisEnabledComponent } from 'app/iris/manage/settings/shared/iris-enabled/iris-enabled.component';
import { AthenaEnabledComponent } from 'app/course/manage/control-center/athena-enabled/athena-enabled.component';
import { MockComponent, MockDirective } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { ComponentRef } from '@angular/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { DialogService } from 'primeng/dynamicdialog';

describe('ControlCenterComponent', () => {
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
                    imports: [MockDirective(TranslateDirective), MockComponent(IrisLogoComponent), MockComponent(IrisEnabledComponent), MockComponent(AthenaEnabledComponent)],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ControlCenterComponent);
        componentRef = fixture.componentRef;
        componentRef.setInput('course', course);
        componentRef.setInput('irisEnabled', true);
        componentRef.setInput('athenaEnabled', true);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should display a panel per enabled AI feature when the user is at least an instructor', () => {
        expect(fixture.debugElement.nativeElement.querySelectorAll('.ai-panel')).toHaveLength(2);
    });

    it('should not display the iris panel when iris is disabled', () => {
        componentRef.setInput('irisEnabled', false);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.directive(IrisEnabledComponent))).toBeFalsy();
        expect(fixture.debugElement.query(By.directive(AthenaEnabledComponent))).toBeTruthy();
    });

    it('should not display the athena panel when athena is disabled', () => {
        componentRef.setInput('athenaEnabled', false);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.directive(AthenaEnabledComponent))).toBeFalsy();
        expect(fixture.debugElement.query(By.directive(IrisEnabledComponent))).toBeTruthy();
    });

    it('should not display any panel when the user is not an instructor', () => {
        componentRef.setInput('course', { id: 1, isAtLeastInstructor: false } as Course);
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelectorAll('.ai-panel')).toHaveLength(0);
    });

    it('should display the iris enabled component with correct inputs', () => {
        const irisEnabledComponent = fixture.debugElement.query(By.directive(IrisEnabledComponent));
        expect(irisEnabledComponent).toBeTruthy();
        expect(irisEnabledComponent.componentInstance.course()).toEqual(course);
    });

    it('should display the athena enabled component with correct inputs', () => {
        const athenaEnabledComponent = fixture.debugElement.query(By.directive(AthenaEnabledComponent));
        expect(athenaEnabledComponent).toBeTruthy();
        expect(athenaEnabledComponent.componentInstance.course()).toEqual(course);
    });

    it('should display the iris logo', () => {
        const irisLogoComponent = fixture.debugElement.query(By.directive(IrisLogoComponent));
        expect(irisLogoComponent).toBeTruthy();
    });

    it('should not display a logo for athena', () => {
        componentRef.setInput('irisEnabled', false);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.directive(IrisLogoComponent))).toBeFalsy();
    });
});
