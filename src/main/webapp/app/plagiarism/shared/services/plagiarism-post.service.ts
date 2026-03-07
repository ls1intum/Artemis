import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PlagiarismPostCreationDtoModel } from 'app/plagiarism/shared/entities/plagiarism-post-creation-dto.model';
import { PlagiarismPostCreationResponseDtoModel, mapResponseToPost } from 'app/plagiarism/shared/entities/plagiarism-post-creation-response-dto.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class PlagiarismPostService {
    private http = inject(HttpClient);

    createPlagiarismPost(courseId: number, dto: PlagiarismPostCreationDtoModel): Observable<Post> {
        return this.http.post<PlagiarismPostCreationResponseDtoModel>(`api/plagiarism/courses/${courseId}/posts`, dto).pipe(map(mapResponseToPost));
    }
}
