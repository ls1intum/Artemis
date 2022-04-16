import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { PlagiarismCasesStudentViewComponent } from 'app/course/plagiarism-cases/student-view/plagiarism-cases-student-view.component';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ActivatedRoute } from '@angular/router';
import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { TranslateService } from '@ngx-translate/core';

describe('Plagiarism Cases Student View Component', () => {
    let component: PlagiarismCasesStudentViewComponent;
    let fixture: ComponentFixture<PlagiarismCasesStudentViewComponent>;
    let plagiarismCasesService: PlagiarismCasesService;

    const parentRoute = {
        parent: {
            params: of({ courseId: 1 }),
        },
    } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;

    const plagiarismCase1 = {
        id: 1,
    } as PlagiarismCase;
    const plagiarismCase2 = {
        id: 2,
    } as PlagiarismCase;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule],
            declarations: [PlagiarismCasesStudentViewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismCasesStudentViewComponent);
        component = fixture.componentInstance;
        plagiarismCasesService = fixture.debugElement.injector.get(PlagiarismCasesService);
        jest.spyOn(plagiarismCasesService, 'getPlagiarismCasesForStudent').mockReturnValue(
            of({ body: [plagiarismCase1, plagiarismCase2] }) as Observable<HttpResponse<PlagiarismCase[]>>,
        );
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set plagiarism cases on initialization', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.courseId).toBe(1);
        expect(component.plagiarismCases).toEqual([plagiarismCase1, plagiarismCase2]);
    }));
});
