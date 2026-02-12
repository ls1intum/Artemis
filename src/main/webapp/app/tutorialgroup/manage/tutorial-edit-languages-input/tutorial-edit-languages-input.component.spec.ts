import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialEditLanguagesInputComponent } from './tutorial-edit-languages-input.component';

describe('TutorialEditLanguagesInput', () => {
    let component: TutorialEditLanguagesInputComponent;
    let fixture: ComponentFixture<TutorialEditLanguagesInputComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialEditLanguagesInputComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialEditLanguagesInputComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
