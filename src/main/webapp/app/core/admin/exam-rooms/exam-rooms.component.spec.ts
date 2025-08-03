import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamRoomsComponent } from './exam-rooms.component';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

describe('ExamRoomsComponent', () => {
    let component: ExamRoomsComponent;
    let fixture: ComponentFixture<ExamRoomsComponent>;
    let httpMock: HttpTestingController;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [ExamRoomsComponent],
            imports: [HttpClientTestingModule],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamRoomsComponent);
        component = fixture.componentInstance;
        httpMock = TestBed.inject(HttpTestingController);
        fixture.detectChanges();
    });

    afterEach(() => {
        httpMock.verify();
    });
});
