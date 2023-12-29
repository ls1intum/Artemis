import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { Lecture } from 'app/entities/lecture.model';
import { LectureImportComponent } from 'app/lecture/lecture-import.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';

describe('LectureImportComponent', () => {
    let fixture: ComponentFixture<LectureImportComponent>;
    let comp: LectureImportComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockComponent(NgbPagination)],
            declarations: [LectureImportComponent, MockComponent(ButtonComponent), MockDirective(SortByDirective), MockDirective(SortDirective)],
            providers: [
                // Overwrite MockRouter declaration in ArtemisTestModule which just returns 'testValue'
                {
                    provide: Router,
                    useClass: Router,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LectureImportComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should open a lecture in a new tab', () => {
        const lecture = new Lecture();
        lecture.id = 1;
        lecture.course = { id: 4 };

        const openSpy = jest.spyOn(window, 'open').mockImplementation(() => null);

        comp.openLectureInNewTab(lecture);

        expect(openSpy).toHaveBeenCalledWith('/course-management/4/lectures/1', '_blank');
    });
});
