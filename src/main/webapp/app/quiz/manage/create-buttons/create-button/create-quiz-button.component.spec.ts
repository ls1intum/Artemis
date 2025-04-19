import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CreateQuizButtonComponent } from './create-quiz-button.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';

describe('CreateQuizButtonComponent', () => {
    let component: CreateQuizButtonComponent;
    let fixture: ComponentFixture<CreateQuizButtonComponent>;
    let router: Router;
    let modalService: NgbModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CreateQuizButtonComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CreateQuizButtonComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);
        modalService = TestBed.inject(NgbModal);
        fixture.componentRef.setInput('course', { id: 123 });
        fixture.detectChanges();
    });

    it('should link to exercise creation', () => {
        jest.spyOn(router, 'navigate');
        jest.spyOn(modalService, 'dismissAll');

        component.linkToExerciseCreation();

        expect(modalService.dismissAll).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 123, 'quiz-exercises', 'new']);
    });
});
