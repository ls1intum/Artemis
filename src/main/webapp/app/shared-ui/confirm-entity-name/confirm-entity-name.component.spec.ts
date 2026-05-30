import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ConfirmEntityNameComponent } from 'app/shared-ui/confirm-entity-name/confirm-entity-name.component';
import { Component } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { By } from '@angular/platform-browser';

const expectedEntityName = 'TestEntityName';

@Component({
    template: '<jhi-confirm-entity-name [entityName]="expectedEntityName" confirmationText="artemisApp.confirm" />',
    imports: [ConfirmEntityNameComponent],
})
class ConfirmEntityNameHostComponent {
    expectedEntityName = expectedEntityName;

    control = new FormControl();
}

describe('ConfirmEntityNameComponent', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('NgModel', () => {
        setupTestBed({ zoneless: true });

        let component: ConfirmEntityNameHostComponent;
        let fixture: ComponentFixture<ConfirmEntityNameHostComponent>;
        let confirmEntityNameComponent: ConfirmEntityNameComponent;

        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [ReactiveFormsModule, ConfirmEntityNameComponent],
                providers: [{ provide: TranslateService, useClass: MockTranslateService }],
            }).compileComponents();
            fixture = TestBed.createComponent(ConfirmEntityNameHostComponent);

            component = fixture.componentInstance;

            const confirmEntityNameDebugElement = fixture.debugElement.query(By.directive(ConfirmEntityNameComponent));
            confirmEntityNameComponent = confirmEntityNameDebugElement.componentInstance;
        });

        it('control should be valid with valid input', async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            confirmEntityNameComponent.control.setValue(expectedEntityName);
            fixture.detectChanges();
            await fixture.whenStable();
            expect(confirmEntityNameComponent.control.valid).toBe(true);
        });

        it('control should be invalid with invalid input', async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            confirmEntityNameComponent.control.setValue('');
            fixture.detectChanges();
            await fixture.whenStable();
            expect(confirmEntityNameComponent.control.invalid).toBe(true);
        });

        it('control should be valid for dynamic entity name', async () => {
            component.expectedEntityName = 'OtherTestEntityName';
            fixture.detectChanges();
            await fixture.whenStable();
            confirmEntityNameComponent.control.setValue('OtherTestEntityName');
            fixture.detectChanges();
            await fixture.whenStable();
            expect(confirmEntityNameComponent.control.valid).toBe(true);
        });

        it('control should be invalid for dynamic entity name with previous entity name', async () => {
            component.expectedEntityName = 'OtherTestEntityName';
            fixture.detectChanges();
            await fixture.whenStable();
            confirmEntityNameComponent.control.setValue('TestEntityName');
            fixture.detectChanges();
            await fixture.whenStable();
            expect(confirmEntityNameComponent.control.invalid).toBe(true);
        });
    });

    describe('Component', () => {
        setupTestBed({ zoneless: true });

        let component: ConfirmEntityNameComponent;
        let fixture: ComponentFixture<ConfirmEntityNameComponent>;

        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [FormsModule, ReactiveFormsModule, TranslateDirective],
                providers: [{ provide: TranslateService, useClass: MockTranslateService }],
            }).compileComponents();
            fixture = TestBed.createComponent(ConfirmEntityNameComponent);
            component = fixture.componentInstance;
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should change warning text color', () => {
            fixture.componentRef.setInput('confirmationText', 'artemisApp.confirm');
            fixture.componentRef.setInput('warningTextColor', 'text-danger');
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('label[for="confirm-entity-name"]').classList.contains('text-danger')).toBeTruthy();
        });

        it('should display confirmation text', () => {
            fixture.componentRef.setInput('confirmationText', 'foobar');
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('label[for="confirm-entity-name"]').textContent).toBe('foobar');
        });
    });
});
