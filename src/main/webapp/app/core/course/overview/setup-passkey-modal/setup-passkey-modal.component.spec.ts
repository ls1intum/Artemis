import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

import { SetupPasskeyModalComponent } from './setup-passkey-modal.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('SetupPasskeyModalComponent', () => {
    let component: SetupPasskeyModalComponent;
    let fixture: ComponentFixture<SetupPasskeyModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SetupPasskeyModalComponent],
            declarations: [MockDirective(TranslateDirective)],
            providers: [MockProvider(NgbActiveModal)],
        }).compileComponents();

        fixture = TestBed.createComponent(SetupPasskeyModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
