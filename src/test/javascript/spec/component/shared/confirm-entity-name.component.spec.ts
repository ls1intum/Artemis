import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ConfirmEntityNameComponent } from 'app/shared/confirm-entity-name/confirm-entity-name.component';
import { Component } from '@angular/core';
import { MockDirective } from 'ng-mocks';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

const expectedEntityName = 'TestEntityName';

@Component({
    template: '<jhi-confirm-entity-name [entityName]="expectedEntityName" [formControl]="control" />',
})
class ConfirmEntityNameHostComponent {
    expectedEntityName = expectedEntityName;

    control = new FormControl();
}

describe('ConfirmEntityNameComponent', () => {
    describe('NgModel', () => {
        let component: ConfirmEntityNameHostComponent;
        let fixture: ComponentFixture<ConfirmEntityNameHostComponent>;

        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [FormsModule, ReactiveFormsModule, TranslateModule.forRoot()],
                declarations: [ConfirmEntityNameHostComponent, ConfirmEntityNameComponent, MockDirective(TranslateDirective)],
            }).compileComponents();
            fixture = TestBed.createComponent(ConfirmEntityNameHostComponent);
            component = fixture.componentInstance;
        });

        it('control should be valid with valid input', async () => {
            component.control.setValue(expectedEntityName);
            fixture.detectChanges();
            expect(component.control.valid).toBeTrue();
        });

        it('control should be invalid with invalid input', async () => {
            component.control.setValue('');
            fixture.detectChanges();
            expect(component.control.invalid).toBeTrue();
        });

        it('control should be valid for dynamic entity name', async () => {
            component.expectedEntityName = 'OtherTestEntityName';
            component.control.setValue('OtherTestEntityName');
            fixture.detectChanges();
            expect(component.control.valid).toBeTrue();
        });

        it('control should be invalid for dynamic entity name with previous entity name', async () => {
            component.expectedEntityName = 'OtherTestEntityName';
            component.control.setValue('TestEntityName');
            fixture.detectChanges();
            expect(component.control.invalid).toBeTrue();
        });
    });

    describe('Component', () => {
        let component: ConfirmEntityNameComponent;
        let fixture: ComponentFixture<ConfirmEntityNameComponent>;

        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [FormsModule, ReactiveFormsModule],
                declarations: [ConfirmEntityNameComponent, TranslateDirective],
                providers: [{ provide: TranslateService, useClass: MockTranslateService }],
            }).compileComponents();
            fixture = TestBed.createComponent(ConfirmEntityNameComponent);
            component = fixture.componentInstance;
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should change warning text color', () => {
            component.warningTextColor = 'text-danger';
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('label[for="confirm-entity-name"]').classList.contains('text-danger')).toBeTruthy();
        });

        it('should display confirmation text', () => {
            component.confirmationText = 'foobar';
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('label[for="confirm-entity-name"]').textContent).toBe('foobar');
        });
    });
});
