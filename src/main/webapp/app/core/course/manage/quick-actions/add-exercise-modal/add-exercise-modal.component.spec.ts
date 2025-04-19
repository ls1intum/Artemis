import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AddExerciseModalComponent } from 'app/core/course/manage/quick-actions/add-exercise-modal/add-exercise-modal.component';
import { CreateProgrammingButtonComponent } from 'app/programming/manage/create-buttons/create-button/create-programming-button.component';
import { MockComponent } from 'ng-mocks';
import { ImportProgrammingButtonComponent } from 'app/programming/manage/create-buttons/import-button/import-programming-button.component';

describe('AddExerciseModalComponent', () => {
    let component: AddExerciseModalComponent;
    let fixture: ComponentFixture<AddExerciseModalComponent>;
    const activeModal: NgbActiveModal = {
        dismiss: jest.fn(),
        close: jest.fn(),
        update: jest.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockComponent(CreateProgrammingButtonComponent), MockComponent(ImportProgrammingButtonComponent), AddExerciseModalComponent],
            providers: [
                { provide: NgbActiveModal, useValue: activeModal },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(AddExerciseModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should dismiss on cancel', () => {
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');
        component.cancel();
        expect(dismissSpy).toHaveBeenCalledWith('cancel');
    });

    it('should close on confirm', () => {
        const closeSpy = jest.spyOn(activeModal, 'close');
        component.confirm();
        expect(closeSpy).toHaveBeenCalledWith(true);
    });
});
