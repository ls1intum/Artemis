import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { ConversationDTO, ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { sanitizeStringForMarkdownEditor } from 'app/foundation/util/markdown.util';

export interface MessageSuggestion {
    label: string;
    kind: 'user' | 'channel' | 'exercise' | 'lecture' | 'message' | 'faq' | 'slide' | 'lecture-unit';
    markdown: string;
}

/** Loads course-scoped completion data only when requested. Each composer owns its cache. */
@Injectable()
export class MessageEditorSuggestions {
    private readonly members = inject(CourseManagementService);
    private readonly channels = inject(ChannelService);
    private readonly exercises = inject(ExerciseService);
    private readonly lectures = inject(LectureService);

    private readonly content = new Map<number, Promise<MessageSuggestion[]>>();

    async search(trigger: '@' | '#', query: string, courseId: number, conversation?: ConversationDTO, editing = false): Promise<MessageSuggestion[]> {
        if (trigger === '@') {
            const response = await firstValueFrom(this.members.searchMembersForUserMentions(courseId, query));
            const items: MessageSuggestion[] = (response.body ?? []).map((user) => ({
                label: `@${user.name}`,
                kind: 'user',
                markdown: `[user]${sanitizeStringForMarkdownEditor(user.name)}(${user.login})[/user]`,
            }));
            if (conversation?.type === ConversationType.GROUP_CHAT && !editing && 'all'.startsWith(query.toLowerCase())) {
                items.unshift({ label: '@all', kind: 'user', markdown: '@all' });
            }
            return items.slice(0, 12);
        }
        const items = (await this.loadContent(courseId)).filter((item) => item.label.toLowerCase().includes(query.toLowerCase()));
        if (/^\d+$/.test(query)) {
            items.unshift({ label: `#${query}`, kind: 'message', markdown: `#${query}` });
        }
        // Keep each category discoverable when the trigger is entered without a search term.
        return query ? items.slice(0, 12) : ['channel', 'exercise', 'lecture'].flatMap((kind) => items.filter((item) => item.kind === kind).slice(0, 4));
    }

    private loadContent(courseId: number): Promise<MessageSuggestion[]> {
        let load = this.content.get(courseId);
        if (!load) {
            load = Promise.all([
                firstValueFrom(this.channels.getPublicChannelsOfCourse(courseId)),
                firstValueFrom(this.exercises.getTitlesForCourse(courseId)),
                firstValueFrom(this.lectures.findAllByCourseIdForOverview(courseId)),
            ])
                .then(([channels, exercises, lectures]) => [
                    ...(channels.body ?? []).map((channel): MessageSuggestion => ({
                        label: `#${channel.name}`,
                        kind: 'channel',
                        markdown: `[channel]${sanitizeStringForMarkdownEditor(channel.name)}(${channel.id})[/channel]`,
                    })),
                    ...exercises
                        .filter((exercise) => !!exercise.title)
                        .map((exercise): MessageSuggestion => ({
                            label: exercise.title!,
                            kind: 'exercise',
                            markdown: `[${exercise.type}]${sanitizeStringForMarkdownEditor(exercise.title)}(/courses/${courseId}/exercises/${exercise.id})[/${exercise.type}]`,
                        })),
                    ...lectures
                        .filter((lecture) => !!lecture.title)
                        .map((lecture): MessageSuggestion => ({
                            label: lecture.title!,
                            kind: 'lecture',
                            markdown: `[lecture]${sanitizeStringForMarkdownEditor(lecture.title)}(/courses/${courseId}/lectures/${lecture.id})[/lecture]`,
                        })),
                ])
                .catch((error: unknown) => {
                    this.content.delete(courseId);
                    throw error;
                });
            this.content.set(courseId, load);
        }
        return load;
    }
}
