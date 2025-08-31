import { Injectable, inject } from '@angular/core';
import { Observable, delay, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { Competency, CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/core/course/shared/entities/course.model';

@Injectable({
    providedIn: 'root',
})
export class AgentChatService {
    private courseManagementService = inject(CourseManagementService);
    private competencyService = inject(CompetencyService);

    sendMessage(message: string, courseId: number): Observable<string> {
        const lowerMessage = message.toLowerCase();

        if (this.isCourseDescriptionRequest(lowerMessage)) {
            return this.getCourseDescriptionResponse(courseId);
        }

        if (this.isCourseExercisesRequest(lowerMessage)) {
            return this.getCourseExercisesResponse(courseId);
        }

        if (this.isCompetencyCreationRequest(lowerMessage)) {
            return this.createCompetencyFromMessage(message, courseId);
        }

        return this.generateGeneralResponse();
    }

    /**
     * Checks if the user is asking to see the course description
     */
    private isCourseDescriptionRequest(message: string): boolean {
        const descriptionRequestKeywords = [
            'show course description',
            'what is the course description',
            'course description?',
            "what's the course about",
            'tell me about the course',
            'course details',
            'show me the description',
        ];
        return descriptionRequestKeywords.some((keyword) => message.includes(keyword));
    }

    /**
     * Checks if the user is asking for course exercises
     */
    private isCourseExercisesRequest(message: string): boolean {
        const exerciseKeywords = ['show exercises', 'show me the exercises', 'list exercises', 'course exercises', 'what exercises', 'exercise list', 'exercises in this course'];
        return exerciseKeywords.some((keyword) => message.includes(keyword));
    }

    /**
     * Checks if this is a competency creation request
     */
    private isCompetencyCreationRequest(message: string): boolean {
        const creationKeywords = ['create competency', 'add competency', 'new competency', 'make competency'];
        return creationKeywords.some((keyword) => message.includes(keyword));
    }

    /**
     * Returns the course description to the user
     */
    private getCourseDescriptionResponse(courseId: number): Observable<string> {
        return this.courseManagementService.find(courseId).pipe(
            map((courseResponse: HttpResponse<Course>) => {
                const courseDescription = courseResponse.body?.description?.trim() ?? '';
                if (!courseDescription) {
                    return "📚 **Course Description**\n\nThis course doesn't have a description set up yet.\n\n💡 **Tip:** You can add a course description in the course settings.\n\nHow else can I help you with this course?";
                }
                return `📚 **Course Description**\n\n${courseDescription}\n\nIs there anything specific you'd like me to help you with for this course?`;
            }),
            catchError(() => of("❌ I couldn't retrieve the course description. Please try again or contact your administrator if the problem persists.")),
        );
    }

    /**
     * Returns the course exercises to the user
     */
    private getCourseExercisesResponse(courseId: number): Observable<string> {
        return this.courseManagementService.findWithExercises(courseId).pipe(
            map((courseResponse: HttpResponse<Course>) => {
                const exercises: Exercise[] = courseResponse.body?.exercises || [];
                if (exercises.length === 0) {
                    return "📋 **Course Exercises**\n\nThis course doesn't have any exercises yet.\n\n💡 **Tip:** You can add exercises through the course management interface.\n\nHow else can I help you?";
                }
                const lines = exercises.map((exercise: Exercise, index: number) => {
                    const type = exercise.type || 'Exercise';
                    const dueDate = exercise.dueDate ? dayjs(exercise.dueDate).format('LL') : 'No due date';
                    return `**${index + 1}. ${exercise.title}**\n📝 Type: ${type} | 📅 Due: ${dueDate}\n`;
                });
                return `📋 **Course Exercises** (${exercises.length} total)\n\n${lines.join('\n')}\n\nIs there anything specific you'd like to know about these exercises?`;
            }),
            catchError(() => of("❌ I couldn't retrieve the course exercises. Please try again or contact your administrator if the problem persists.")),
        );
    }

    /**
     * Creates a competency from the user's message
     */
    private createCompetencyFromMessage(message: string, courseId: number): Observable<string> {
        const title = this.extractCompetencyTitle(message);

        if (!title) {
            return of("❌ I couldn't extract a competency title from your message. Please try: 'Create competency for [topic]'").pipe(delay(500));
        }

        const competency = new Competency();
        competency.title = title;
        competency.description = `Competency for ${title}`;
        competency.taxonomy = CompetencyTaxonomy.UNDERSTAND;
        competency.masteryThreshold = 85;
        competency.optional = false;

        return new Observable<string>((subscriber) => {
            this.competencyService.create(competency, courseId).subscribe({
                next: () => {
                    subscriber.next(
                        `✅ **Successfully created competency: "${title}"**\n\nThe competency has been added to your course and is now available for students.\n\nWould you like to create another competency?`,
                    );
                    subscriber.complete();
                },
                error: () => {
                    subscriber.next(
                        `❌ **Error creating competency "${title}"**\n\nI couldn't save the competency to your course. Please try again or contact your administrator if the problem persists.`,
                    );
                    subscriber.complete();
                },
            });
        });
    }

    /**
     * Extracts competency title from user message
     */
    private extractCompetencyTitle(message: string): string {
        const lowerMessage = message.toLowerCase();

        // Look for patterns like "create competency for X" or "add competency for X"
        const patterns = [/create competency for (.+)/, /add competency for (.+)/, /new competency for (.+)/, /make competency for (.+)/];

        for (const pattern of patterns) {
            const match = lowerMessage.match(pattern);
            if (match && match[1]) {
                return this.capitalizeTitle(match[1].trim());
            }
        }

        return '';
    }

    /**
     * Capitalizes the first letter of each word in a title
     */
    private capitalizeTitle(title: string): string {
        return title
            .split(' ')
            .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
            .join(' ');
    }

    /**
     * Generates general helpful responses
     */
    private generateGeneralResponse(): Observable<string> {
        const responses = [
            `I'm here to help you with your course! I can:\n\n📚 **Show course description** - "What is the course description?"\n📋 **List course exercises** - "Show me the exercises"\n✏️ **Create competencies** - "Create competency for [topic]"\n\nWhat would you like to do?`,

            `I can assist you with managing your course. Try asking me:\n\n• "Show course description"\n• "List exercises"\n• "Create competency for algorithms"\n\nHow can I help you today?`,

            `As your course assistant, I can help you with:\n\n📚 Course information and description\n📋 Exercise listings\n✏️ Creating new competencies\n\nWhat would you like to explore?`,
        ];

        const randomResponse = responses[Math.floor(Math.random() * responses.length)];
        return of(randomResponse).pipe(delay(1000));
    }
}
