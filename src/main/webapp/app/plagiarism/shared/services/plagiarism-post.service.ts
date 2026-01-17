import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Post } from 'app/communication/shared/entities/post.model';
import { PlagiarismPostCreationDTO } from 'app/plagiarism/shared/entities/PlagiarismPostCreationDTO';

@Injectable({ providedIn: 'root' })
export class PlagiarismPostService {
    private http = inject(HttpClient);

    createPlagiarismPost(courseId: number, dto: PlagiarismPostCreationDTO): Observable<Post> {
        return this.http.post<Post>(`api/plagiarism/courses/${courseId}/posts`, dto);
    }
}
