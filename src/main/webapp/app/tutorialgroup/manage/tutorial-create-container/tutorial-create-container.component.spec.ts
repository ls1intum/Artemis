import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialCreateContainerComponent } from './tutorial-create-container.component';

describe('TutorialCreateContainer', () => {
    let component: TutorialCreateContainerComponent;
    let fixture: ComponentFixture<TutorialCreateContainerComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialCreateContainerComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialCreateContainerComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
