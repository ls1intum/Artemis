import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export class ParticipantScoreDTO {
    public id?: number;
    public userId?: number;
    public userName?: string;
    public teamId?: number;
    public teamName?: string;
    public exerciseId?: number;
    public exerciseTitle?: string;
    public lastResultId?: number;
    public lastResultScore?: number;
    public lastPoints?: number;
    public lastRatedResultId?: number;
    public lastRatedResultScore?: number;
    public lastRatedPoints?: number;
}

export class ParticipantScoreAverageDTO {
    public id?: number;
    public name?: string;
    public averageScore?: number;
    public averageRatedScore?: number;
    public averagePoints?: number;
    public averageRatedPoints?: number;
    public averageGrade?: string;
    public averageRatedGrade?: string;
}

export class ScoresDTO {
    public studentId?: number;
    public studentLogin?: string;
    public pointsAchieved?: number;
    public scoreAchieved?: number;
    public regularPointsAchievable?: number;
}
export type SortDirection = 'asc' | 'desc';
export type SortProperty = keyof ParticipantScoreDTO;
export type SortingParameter = {
    sortProperty: SortProperty;
    sortDirection: SortDirection;
};

@Injectable({ providedIn: 'root' })
export class ParticipantScoresService {
    public resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient) {}

    findCourseScores(courseId: number): Observable<HttpResponse<ScoresDTO[]>> {
        return this.http.get<ScoresDTO[]>(`${this.resourceUrl}/courses/${courseId}/course-scores`, {
            observe: 'response',
        });
    }

    findExamScores(examId: number): Observable<HttpResponse<ScoresDTO[]>> {
        return this.http.get<ScoresDTO[]>(`${this.resourceUrl}/exams/${examId}/exam-scores`, {
            observe: 'response',
        });
    }

    findAllOfCoursePaged(courseId: number, sortingParameters: SortingParameter[], page: number, size: number): Observable<HttpResponse<ParticipantScoreDTO[]>> {
        const params = this.createPageParameters(sortingParameters, page, size);
        return this.http.get<ParticipantScoreDTO[]>(`${this.resourceUrl}/courses/${courseId}/participant-scores`, {
            observe: 'response',
            params,
        });
    }

    findAllOfCourse(courseId: number): Observable<HttpResponse<ParticipantScoreDTO[]>> {
        let params = new HttpParams();
        params = params.set('getUnpaged', 'true');
        return this.http.get<ParticipantScoreDTO[]>(`${this.resourceUrl}/courses/${courseId}/participant-scores`, {
            observe: 'response',
            params,
        });
    }

    findAverageOfCourse(courseId: number, onlyConsiderRatedScores = true): Observable<HttpResponse<number>> {
        let params = new HttpParams();
        params = params.set('onlyConsiderRatedScores', onlyConsiderRatedScores + '');
        return this.http.get<number>(`${this.resourceUrl}/courses/${courseId}/participant-scores/average`, {
            observe: 'response',
            params,
        });
    }

    findAverageOfCoursePerParticipant(courseId: number): Observable<HttpResponse<ParticipantScoreAverageDTO[]>> {
        return this.http.get<ParticipantScoreAverageDTO[]>(`${this.resourceUrl}/courses/${courseId}/participant-scores/average-participant`, {
            observe: 'response',
        });
    }

    findAllOfExamPaged(examId: number, sortingParameters: SortingParameter[], page: number, size: number): Observable<HttpResponse<ParticipantScoreDTO[]>> {
        const params = this.createPageParameters(sortingParameters, page, size);
        return this.http.get<ParticipantScoreDTO[]>(`${this.resourceUrl}/courses/${examId}/participant-scores`, {
            observe: 'response',
            params,
        });
    }

    findAllOfExam(examId: number): Observable<HttpResponse<ParticipantScoreDTO[]>> {
        let params = new HttpParams();
        params = params.set('getUnpaged', 'true');
        return this.http.get<ParticipantScoreDTO[]>(`${this.resourceUrl}/exams/${examId}/participant-scores`, {
            observe: 'response',
            params,
        });
    }

    findAverageOfExam(examId: number, onlyConsiderRatedScores = true): Observable<HttpResponse<number>> {
        let params = new HttpParams();
        params = params.set('onlyConsiderRatedScores', onlyConsiderRatedScores + '');
        return this.http.get<number>(`${this.resourceUrl}/exams/${examId}/participant-scores/average`, {
            observe: 'response',
            params,
        });
    }

    findAverageOfExamPerParticipant(examId: number): Observable<HttpResponse<ParticipantScoreAverageDTO[]>> {
        return this.http.get<ParticipantScoreAverageDTO[]>(`${this.resourceUrl}/exams/${examId}/participant-scores/average-participant`, {
            observe: 'response',
        });
    }

    private createPageParameters(sortingParameters: SortingParameter[], page: number, size: number) {
        let params = new HttpParams();
        for (const sortParameter of sortingParameters) {
            params = params.append('sort', `${sortParameter.sortProperty},${sortParameter.sortDirection}`);
        }
        params = params.set('page', String(page));
        return params.set('size', String(size));
    }
}
