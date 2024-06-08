import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { Prerequisite, PrerequisiteResponseDTO } from 'app/entities/prerequisite.model';

describe('PrerequisiteService', () => {
    let prerequisiteService: PrerequisiteService;
    let httpTestingController: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [],
        });

        prerequisiteService = TestBed.inject(PrerequisiteService);
        httpTestingController = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should find all prerequisites', fakeAsync(() => {
        let actualPrerequisites: any;
        const expectedPrerequisites: Prerequisite[] = [
            { id: 1, title: 'title1' },
            { id: 2, title: 'title2' },
        ];
        const returnedFromService: Prerequisite[] = [...expectedPrerequisites];
        prerequisiteService.getAllPrerequisitesForCourse(1).subscribe((resp) => (actualPrerequisites = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(actualPrerequisites).toEqual(expectedPrerequisites);
    }));

    it('should import prerequisites', fakeAsync(() => {
        let actualPrerequisites: any;
        const expectedPrerequisites: Prerequisite[] = [
            { id: 3, title: 'title1' },
            { id: 4, title: 'title2' },
        ];
        const returnedFromService: Prerequisite[] = [...expectedPrerequisites];
        prerequisiteService.importPrerequisites([1, 2], 1).subscribe((resp) => (actualPrerequisites = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(actualPrerequisites).toEqual(expectedPrerequisites);
    }));

    it('should remove a prerequisite', fakeAsync(() => {
        let result: any;
        prerequisiteService.deletePrerequisite(1, 1).subscribe((resp) => (result = resp.ok));
        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();

        expect(result).toBeTrue();
    }));

    it('should create a prerequisite', fakeAsync(() => {
        let actualPrerequisite: Prerequisite | undefined;
        const expectedPrerequisite: Prerequisite = { id: 1, title: 'newTitle', description: 'newDescription' };
        const returnedFromService: Prerequisite = { ...expectedPrerequisite };

        prerequisiteService.createPrerequisite({ title: 'newTitle', description: 'newDescription' }, 1).subscribe((resp) => (actualPrerequisite = resp));
        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(actualPrerequisite).toEqual(expectedPrerequisite);
    }));

    it('should update a prerequisite', fakeAsync(() => {
        let actualPrerequisite: Prerequisite | undefined;
        const expectedPrerequisite: Prerequisite = { id: 1, title: 'newTitle', description: 'newDescription' };
        const returnedFromService: Prerequisite = { ...expectedPrerequisite };

        prerequisiteService.createPrerequisite({ title: 'newTitle', description: 'newDescription' }, 1, 1).subscribe((resp) => (actualPrerequisite = resp));
        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(actualPrerequisite).toEqual(expectedPrerequisite);
    }));
  
    it('should convert response dtos to to prerequisite', () => {
        const expectedPrerequisite1: Prerequisite = { id: 1, title: 'title1', linkedCourseCompetency: { id: 1, course: { id: 1, title: '', semester: 'SS01' } } };
        const prerequisiteDTO1: PrerequisiteResponseDTO = {
            id: 1,
            title: 'title1',
            linkedCourseCompetencyDTO: {
                id: 1,
                courseId: 1,
                courseTitle: '',
                semester: 'SS01',
            },
        };
        const expectedPrerequisite2: Prerequisite = { id: 2, title: 'title2' };
        const prerequisiteDTO2: PrerequisiteResponseDTO = { id: 2, title: 'title2' };

        const actualPrerequisite1 = PrerequisiteService['convertResponseDTOToPrerequisite'](prerequisiteDTO1);
        const actualPrerequisite2 = PrerequisiteService['convertResponseDTOToPrerequisite'](prerequisiteDTO2);

        expect(actualPrerequisite1).toEqual(expectedPrerequisite1);
        expect(actualPrerequisite2).toEqual(expectedPrerequisite2);
    });
});
