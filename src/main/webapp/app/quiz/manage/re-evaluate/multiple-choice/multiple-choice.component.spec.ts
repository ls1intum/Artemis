import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MultipleChoiceComponent } from './multiple-choice.component';

describe('MultipleChoice', () => {
    let component: MultipleChoiceComponent;
    let fixture: ComponentFixture<MultipleChoiceComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MultipleChoiceComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(MultipleChoiceComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
