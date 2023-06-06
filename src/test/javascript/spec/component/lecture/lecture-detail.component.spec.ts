import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { faFile, faPencilAlt, faPuzzlePiece } from '@fortawesome/free-solid-svg-icons';
import { of } from 'rxjs';
import { LectureDetailComponent } from 'app/lecture/lecture-detail.component';
import { Lecture } from 'app/entities/lecture.model';
import { MockModule, MockPipe } from 'ng-mocks';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';

import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

describe('LectureDetailComponent', () => {
    let component: LectureDetailComponent;
    let fixture: ComponentFixture<LectureDetailComponent>;
    let mockActivatedRoute: any;

    beforeEach(async () => {
        mockActivatedRoute = {
            data: of({ lecture: new Lecture() }), // Mock the ActivatedRoute data observable
        };

        await TestBed.configureTestingModule({
            declarations: [LectureDetailComponent, HtmlForMarkdownPipe, MockPipe(ArtemisDatePipe), FontAwesomeTestingModule, MockModule(RouterModule)],
            providers: [{ provide: ActivatedRoute, useValue: mockActivatedRoute }],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(LectureDetailComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should initialize lecture when ngOnInit is called', () => {
        const mockLecture = new Lecture();
        mockLecture.title = 'Test Lecture';
        mockLecture.description = 'Test Description';

        mockActivatedRoute.data = of({ lecture: mockLecture }); // Update the ActivatedRoute mock data

        component.ngOnInit();

        expect(component.lecture).toEqual(mockLecture);
    });

    it('should have the correct icons initialized', () => {
        expect(component.faPencilAlt).toEqual(faPencilAlt);
        expect(component.faFile).toEqual(faFile);
        expect(component.faPuzzlePiece).toEqual(faPuzzlePiece);
    });
});
