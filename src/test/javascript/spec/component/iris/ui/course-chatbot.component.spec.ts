import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CourseChatbotComponent } from './course-chatbot.component';

describe('CourseChatbotComponent', () => {
    let component: CourseChatbotComponent;
    let fixture: ComponentFixture<CourseChatbotComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseChatbotComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseChatbotComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
