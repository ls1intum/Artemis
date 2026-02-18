import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialSessionCreateOrEditModalComponent } from './tutorial-session-create-or-edit-modal.component';

describe('TutorialGroupSessionCreateOrEditModal', () => {
    let component: TutorialSessionCreateOrEditModalComponent;
    let fixture: ComponentFixture<TutorialSessionCreateOrEditModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialSessionCreateOrEditModalComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialSessionCreateOrEditModalComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
