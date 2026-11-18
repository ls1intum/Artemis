import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Observable, of } from 'rxjs';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { PageableResult, ParticipationSearch } from 'app/foundation/pagination/pageable-table';
import { ParticipationManagementDTO } from 'app/exercise/participation/participation-management-dto.model';
import { ParticipationNameExportDTO } from 'app/exercise/exercise-scores/participation-name-export-dto.model';

export class MockParticipationService {
    findWithLatestResult = (participationId: number) => of({} as Participation);
    mergeStudentParticipations = (participations: StudentParticipation[]) => participations;
    getSpecificStudentParticipation = (studentParticipations: StudentParticipation[], testRun: boolean) =>
        studentParticipations.filter((participation) => !!participation.testRun === testRun).first();

    searchParticipations = (exerciseId: number, search: ParticipationSearch): Observable<PageableResult<ParticipationManagementDTO>> => of({ content: [], totalElements: 0 });

    getParticipationNamesForExport = (exerciseId: number): Observable<ParticipationNameExportDTO[]> => of([]);
}
