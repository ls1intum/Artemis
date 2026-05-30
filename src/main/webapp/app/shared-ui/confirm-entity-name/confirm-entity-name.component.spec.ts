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
    template: '<jhi-confirm-entity-name [entityName]="expectedEntityName" />',
    imports: [ConfirmEntityNameComponent],
})
class ConfirmEntityNameHostComponent {
    expectedEntityName = expectedEntityName;

    control = new FormControl();
}

describe('ConfirmEntityNameComponent', () => {
    setupTestBed({ zoneless: true });
    describe('NgModel', () => {
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
            component.expectedEntityName = expectedEntityName;
            fixture.changeDetectorRef.detectChanges();
            confirmEntityNameComponent.control.setValue(expectedEntityName);
            fixture.changeDetectorRef.detectChanges();
            expect(confirmEntityNameComponent.control.valid).toBeTruthy();
        });

        it('control should be invalid with invalid input', async () => {
            component.expectedEntityName = expectedEntityName;
            fixture.changeDetectorRef.detectChanges();
            confirmEntityNameComponent.control.setValue('');
            fixture.changeDetectorRef.detectChanges();
            expect(confirmEntityNameComponent.control.invalid).toBeTruthy();
        });

        it('control should be valid for dynamic entity name', async () => {
            component.expectedEntityName = 'OtherTestEntityName';
            fixture.changeDetectorRef.detectChanges();
            confirmEntityNameComponent.control.setValue('OtherTestEntityName');
            fixture.changeDetectorRef.detectChanges();
            expect(confirmEntityNameComponent.control.valid).toBeTruthy();
        });

        it('control should be invalid for dynamic entity name with previous entity name', async () => {
            component.expectedEntityName = 'OtherTestEntityName';
            fixture.changeDetectorRef.detectChanges();
            confirmEntityNameComponent.control.setValue('TestEntityName');
            fixture.changeDetectorRef.detectChanges();
            expect(confirmEntityNameComponent.control.invalid).toBeTruthy();
        });
    });

    describe('Component', () => {
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
            fixture.componentRef.setInput('warningTextColor', 'text-danger');
            fixture.changeDetectorRef.detectChanges();
            expect(fixture.nativeElement.querySelector('label[for="confirm-entity-name"]').classList.contains('text-danger')).toBeTruthy();
        });

        it('should display confirmation text', () => {
            fixture.componentRef.setInput('confirmationText', 'foobar');
            fixture.changeDetectorRef.detectChanges();
            expect(fixture.nativeElement.querySelector('label[for="confirm-entity-name"]').textContent).toBe('foobar');
        });
    });
});
