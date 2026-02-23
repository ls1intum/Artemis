import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PlagiarismPostCreationDTO } from 'app/plagiarism/shared/entities/PlagiarismPostCreationDTO';
import { PlagiarismPostCreationResponseDTO, mapResponseToPost } from 'app/plagiarism/shared/entities/PlagiarismPostCreationResponseDTO';
import { Post } from 'app/communication/shared/entities/post.model';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class PlagiarismPostService {
    private http = inject(HttpClient);

    createPlagiarismPost(courseId: number, dto: PlagiarismPostCreationDTO): Observable<Post> {
        return this.http.post<PlagiarismPostCreationResponseDTO>(`api/plagiarism/courses/${courseId}/posts`, dto).pipe(map(mapResponseToPost));
    }
}
