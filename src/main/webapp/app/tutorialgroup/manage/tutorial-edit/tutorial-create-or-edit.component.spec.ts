import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialCreateOrEditComponent } from './tutorial-create-or-edit.component';

describe('TutorialEdit', () => {
    let component: TutorialCreateOrEditComponent;
    let fixture: ComponentFixture<TutorialCreateOrEditComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialCreateOrEditComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialCreateOrEditComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
