import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { PlagiarismAnswerPostCreationDTO } from 'app/plagiarism/shared/entities/PlagiarismAnswerPostCreationDTO';

@Injectable({ providedIn: 'root' })
export class PlagiarismAnswerPostService {
    private http = inject(HttpClient);

    createAnswerPost(courseId: number, dto: PlagiarismAnswerPostCreationDTO): Observable<AnswerPost> {
        return this.http.post<AnswerPost>(`api/plagiarism/courses/${courseId}/answer-posts`, dto);
    }
}
