import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FitTextDirective } from 'app/exercises/quiz/shared/fit-text/fit-text.directive';

@Component({
    template: ` <div style="align-content: center;">
        <div style="width: 20%; height: 20%; margin: 0 auto;">
            <div fitText>test</div>
        </div>
    </div>`,
})
class TestFitTextComponent {}

describe('FitTextDirective', () => {
    let fixture: ComponentFixture<TestFitTextComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [TestFitTextComponent, FitTextDirective],
        });
        fixture = TestBed.createComponent(TestFitTextComponent);
    });

    it('should create an instance', () => {
        expect(fixture).toBeTruthy();
        // TODO: extend test case
    });
});
