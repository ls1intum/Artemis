import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Params } from '@angular/router';
import { CourseWideContext, PageType, PostContextFilter, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { combineLatest, Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ButtonType } from 'app/shared/components/button.component';
import { HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { faFilter, faLongArrowAltDown, faLongArrowAltUp, faPlus, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';

@Component({
    selector: 'jhi-course-discussion',
    templateUrl: './course-discussion.component.html',
})
export class CourseDiscussionComponent {}
