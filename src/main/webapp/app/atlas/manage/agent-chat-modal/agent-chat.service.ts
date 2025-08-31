import { Injectable, inject } from '@angular/core';
import { Observable, delay, map, of } from 'rxjs';
import { Competency, CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { CompetencyDraft } from 'app/atlas/shared/entities/chat-message.model';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { firstValueFrom } from 'rxjs';
import dayjs from 'dayjs/esm';

export interface AgentChatRequest {
    message: string;
    courseId: number;
    sessionId: string;
}

export interface AgentChatResponse {
    response: string;
    sessionId: string;
}

export interface AgentCompetencySuggestionRequest {
    requestText: string;
    courseId: number;
    sessionId: string;
}

export interface AgentCompetencySuggestionResponse {
    response: string;
    competencies: any[];
    sessionId: string;
    hasCompetencies: boolean;
    confirmationPrompt?: string;
}

@Injectable({
    providedIn: 'root',
})
export class AgentChatService {
    private courseCompetencyService = inject(CourseCompetencyService);
    private courseManagementService = inject(CourseManagementService);
    private competencyService = inject(CompetencyService);
    private websocketService = inject(WebsocketService);
    private pendingCompetencies: CompetencyDraft[] = [];
    private isGeneratingFromDescription = false;

    sendMessage(message: string, courseId: number): Observable<string> {
        const lowerMessage = message.toLowerCase();

        // Handle competency creation confirmation
        if (lowerMessage.includes('yes') || lowerMessage.includes('create') || lowerMessage.includes('confirm')) {
            if (this.pendingCompetencies.length > 0) {
                return this.createPendingCompetencies(courseId);
            }
        }

        // Check if user is asking for course description
        if (this.isCourseDescriptionRequest(lowerMessage)) {
            return this.getCourseDescriptionResponse(courseId);
        }

        // Check if this is a competency-related request
        if (this.isCompetencyRequest(lowerMessage)) {
            return this.sendCompetencySuggestionRequest(message, courseId);
        }

        // Send general chat request to agent
        return this.sendChatRequest(message, courseId);
    }

    private sendChatRequest(message: string, courseId: number): Observable<string> {
        // Mock response based on the message content
        return this.generateGeneralResponse(message).pipe(delay(800));
    }

    private sendCompetencySuggestionRequest(message: string, courseId: number): Observable<string> {
        // Check if user wants to generate from course description
        if (this.isGenerateFromDescriptionRequest(message)) {
            return this.generateFromCourseDescription(courseId);
        }

        // Generate manual competency response for specific topics
        return this.generateCompetencyResponse(message, courseId);
    }

    /**
     * Checks if the message is requesting generation from course description
     */
    private isGenerateFromDescriptionRequest(message: string): boolean {
        const descriptionKeywords = ['course description', 'from description', 'based on course', 'course content', 'course syllabus'];
        return descriptionKeywords.some((keyword) => message.toLowerCase().includes(keyword));
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
        return descriptionRequestKeywords.some((keyword) => message.toLowerCase().includes(keyword));
    }

    /**
     * Returns the course description to the user
     */
    private getCourseDescriptionResponse(courseId: number): Observable<string> {
        return new Observable<string>((subscriber) => {
            this.getCourseDescription(courseId)
                .then((courseDescription) => {
                    if (!courseDescription || courseDescription.trim().length === 0) {
                        subscriber.next(
                            "📚 **Course Description**\n\nThis course doesn't have a description set up yet.\n\n💡 **Tip:** You can add a course description in the course settings, and then I'll be able to generate competencies automatically based on it!\n\nIn the meantime, I can help you create competencies for specific topics. Just tell me what subjects you'd like to cover.",
                        );
                        subscriber.complete();
                        return;
                    }

                    const response = `📚 **Course Description**\n\n${courseDescription}\n\n💡 **Would you like me to generate competencies based on this description?**\n\nJust say "Generate competencies from course description" and I'll analyze this content to create relevant learning objectives for your students.`;

                    subscriber.next(response);
                    subscriber.complete();
                })
                .catch(() => {
                    subscriber.next("❌ I couldn't retrieve the course description. Please try again or contact your administrator if the problem persists.");
                    subscriber.complete();
                });
        });
    }

    /**
     * Generates competencies from the course description using the real API
     */
    private generateFromCourseDescription(courseId: number): Observable<string> {
        if (this.isGeneratingFromDescription) {
            return of("I'm already generating competencies from the course description. Please wait for the current generation to complete.").pipe(delay(500));
        }

        this.isGeneratingFromDescription = true;

        return new Observable<string>((subscriber) => {
            this.getCourseDescription(courseId)
                .then((courseDescription) => {
                    if (!courseDescription) {
                        subscriber.next(
                            "I couldn't find a course description. Please ensure your course has a description set up, or provide specific topics you'd like me to create competencies for.",
                        );
                        subscriber.complete();
                        this.isGeneratingFromDescription = false;
                        return;
                    }

                    subscriber.next(`🤖 **Analyzing course description...**\n\nI found your course description
                     and I'm now generating competencies based on it. This may take a moment while I:\n\n✨ Analyze
                     the course content\n🎯 Identify learning objectives\n📚 Create structured competencies\n\n
                     Please wait while I work on this...`);

                    this.getCurrentCompetencies(courseId).subscribe((currentCompetencies) => {
                        this.courseCompetencyService.generateCompetenciesFromCourseDescription(courseId, courseDescription, currentCompetencies).subscribe({
                            next: () => {
                                const websocketTopic = `/user/topic/iris/competencies/${courseId}`;
                                this.websocketService.subscribe(websocketTopic);
                                this.websocketService.receive(websocketTopic).subscribe({
                                    next: (update: any) => {
                                        if (update.result) {
                                            const generatedCompetencies = this.convertToCompetencyDrafts(update.result);
                                            this.pendingCompetencies = generatedCompetencies;

                                            let response = `🎉 **Great! I've generated ${generatedCompetencies.length} competencies from your course description:**\n\n`;

                                            generatedCompetencies.forEach((comp, index) => {
                                                const taxonomyLabel = this.getTaxonomyLabel(comp.taxonomy);
                                                response += `**${index + 1}. ${comp.title}**\n`;
                                                response += `📝 ${comp.description}\n`;
                                                response += `🎯 **Level:** ${taxonomyLabel} | **Mastery:** ${comp.masteryThreshold}%\n\n`;
                                            });

                                            response += '✅ **Should I create these competencies for your course?**\n';
                                            response += '*Just say "yes" or "create them" to proceed!*';

                                            subscriber.next(response);
                                            subscriber.complete();
                                        }

                                        if (update.stages && update.stages.some((stage: IrisStageDTO) => stage.state === IrisStageStateDTO.ERROR)) {
                                            subscriber.next(
                                                '❌ **Generation Failed**\n\n' +
                                                    "I couldn't generate competencies from the course description. This might be due to:\n\n" +
                                                    '• AI service not being available\n' +
                                                    '• Server configuration issues\n' +
                                                    '• Network connectivity problems\n\n' +
                                                    "💡 **Alternative:** I can still help you create competencies manually! Just tell me what topics you'd like to cover.",
                                            );
                                            subscriber.complete();
                                            this.websocketService.unsubscribe(websocketTopic);
                                            this.isGeneratingFromDescription = false;
                                        }

                                        if (
                                            update.stages &&
                                            update.stages.every(
                                                (stage: IrisStageDTO) => stage.state !== IrisStageStateDTO.NOT_STARTED && stage.state !== IrisStageStateDTO.IN_PROGRESS,
                                            )
                                        ) {
                                            this.websocketService.unsubscribe(websocketTopic);
                                            this.isGeneratingFromDescription = false;
                                        }
                                    },
                                    error: (error) => {
                                        subscriber.next(
                                            '❌ **Connection Error**\n\n' +
                                                'I lost connection while generating competencies. This could be due to:\n\n' +
                                                '• Network connectivity issues\n' +
                                                '• Server timeout\n' +
                                                '• Websocket connection failure\n\n' +
                                                "💡 **Try again:** You can retry by saying 'Generate competencies from course description' again.",
                                        );
                                        subscriber.complete();
                                        this.websocketService.unsubscribe(websocketTopic);
                                        this.isGeneratingFromDescription = false;
                                    },
                                });
                            },
                            error: (error) => {
                                let errorMessage = '❌ **API Error**\n\n';

                                if (error?.status === 404) {
                                    errorMessage +=
                                        'The competency generation service is not available. This might be because:\n\n' +
                                        '• IRIS AI profile is not enabled for this server\n' +
                                        '• The feature is not configured properly\n\n';
                                } else if (error?.status === 403) {
                                    errorMessage += "You don't have permission to use the AI competency generation feature.\n\n";
                                } else if (error?.status === 500) {
                                    errorMessage += 'The server encountered an internal error while processing your request.\n\n';
                                } else {
                                    errorMessage += `Request failed with error: ${error?.message || 'Unknown error'}\n\n`;
                                }

                                errorMessage += "💡 **Alternative:** I can still help you create competencies manually! Just tell me what topics you'd like to cover.";

                                subscriber.next(errorMessage);
                                subscriber.complete();
                                this.isGeneratingFromDescription = false;
                            },
                        });
                    });
                })
                .catch(() => {
                    subscriber.next("I couldn't access the course information." + " Please try again or provide specific topics you'd like me to create competencies for.");
                    subscriber.complete();
                    this.isGeneratingFromDescription = false;
                });
        });
    }

    /**
     * Gets the course description
     */
    private async getCourseDescription(courseId: number): Promise<string> {
        try {
            const courseResponse = await firstValueFrom(this.courseManagementService.find(courseId));
            return courseResponse.body?.description ?? '';
        } catch (error) {
            return '';
        }
    }

    /**
     * Returns the current competencies in the course
     */
    private getCurrentCompetencies(courseId: number): Observable<any[]> {
        const courseCompetenciesObservable = this.courseCompetencyService.getAllForCourse(courseId);
        if (courseCompetenciesObservable) {
            return courseCompetenciesObservable.pipe(
                map((competencies) => competencies.body?.map((c) => ({ title: c.title, description: c.description, taxonomy: c.taxonomy })) ?? []),
            );
        }
        return of([]);
    }

    /**
     * Converts API response competencies to CompetencyDraft format
     */
    private convertToCompetencyDrafts(competencies: any[]): CompetencyDraft[] {
        return competencies.map((comp) => ({
            title: comp.title || comp.name,
            description: comp.description,
            taxonomy: this.mapToTaxonomy(comp.taxonomy),
            masteryThreshold: comp.masteryThreshold || 85,
            optional: comp.optional || false,
            softDueDate: dayjs().add(2, 'weeks'),
        }));
    }

    /**
     * Maps taxonomy string to enum
     */
    private mapToTaxonomy(taxonomyString?: string): CompetencyTaxonomy {
        if (!taxonomyString) return CompetencyTaxonomy.UNDERSTAND;

        const upper = taxonomyString.toUpperCase();
        if (upper.includes('REMEMBER')) return CompetencyTaxonomy.REMEMBER;
        if (upper.includes('UNDERSTAND')) return CompetencyTaxonomy.UNDERSTAND;
        if (upper.includes('APPLY')) return CompetencyTaxonomy.APPLY;
        if (upper.includes('ANALYZE')) return CompetencyTaxonomy.ANALYZE;
        if (upper.includes('EVALUATE')) return CompetencyTaxonomy.EVALUATE;
        if (upper.includes('CREATE')) return CompetencyTaxonomy.CREATE;

        return CompetencyTaxonomy.UNDERSTAND; // Default
    }

    private isCompetencyRequest(message: string): boolean {
        const competencyKeywords = ['competenc', 'learning objective', 'skill', 'knowledge', 'create', 'generate', 'suggest', 'help me with', 'course', 'topic', 'subject'];

        return competencyKeywords.some((keyword) => message.includes(keyword));
    }

    private generateCompetencyResponse(message: string, courseId: number): Observable<string> {
        // Extract competency information from the message
        const competencies = this.parseCompetencyRequest(message);
        this.pendingCompetencies = competencies;

        if (competencies.length === 0) {
            return of(this.getCompetencyPrompt()).pipe(delay(1500));
        }

        let response = `Great! Based on your request, I can help you create ${competencies.length} competencies:\n\n`;

        competencies.forEach((comp, index) => {
            const taxonomyLabel = this.getTaxonomyLabel(comp.taxonomy);
            const dueDate = comp.softDueDate?.format('MMM DD, YYYY') || 'No due date';
            const optionalLabel = comp.optional ? ' (Optional)' : '';

            response += `**${index + 1}. ${comp.title}**${optionalLabel}\n`;
            response += `📝 ${comp.description}\n`;
            response += `🎯 **Level:** ${taxonomyLabel} | **Mastery:** ${comp.masteryThreshold}% | **Due:** ${dueDate}\n\n`;
        });

        response += '✅ **Should I create these competencies for your course?**\n';
        response += '*Just say "yes" or "create them" to proceed!*';

        return of(response).pipe(delay(1500));
    }

    private parseCompetencyRequest(message: string): CompetencyDraft[] {
        const competencies: CompetencyDraft[] = [];
        const lowerMessage = message.toLowerCase();

        // Simple parsing - look for common educational topics and create basic competencies
        const topics = this.extractTopics(lowerMessage);
        const currentDate = dayjs();

        topics.forEach((topic, index) => {
            const dueDate = currentDate.add((index + 1) * 2, 'weeks');
            competencies.push({
                title: topic.title,
                description: topic.description,
                taxonomy: topic.taxonomy,
                masteryThreshold: topic.masteryThreshold || 85,
                optional: topic.optional || false,
                softDueDate: dueDate,
            });
        });

        // If no specific topics detected, create generic competencies based on the message
        if (competencies.length === 0 && message.length > 10) {
            competencies.push({
                title: this.generateTitleFromMessage(message),
                description: `Understanding and applying concepts related to: ${message}`,
                taxonomy: CompetencyTaxonomy.UNDERSTAND,
                masteryThreshold: 85,
                optional: false,
                softDueDate: currentDate.add(2, 'weeks'),
            });
        }

        return competencies;
    }

    private extractTopics(message: string): any[] {
        const topics = [];

        if (message.includes('programming') || message.includes('coding') || message.includes('development')) {
            topics.push({
                title: 'Programming Fundamentals',
                description: 'Understanding basic programming concepts, syntax, and problem-solving techniques.',
                taxonomy: CompetencyTaxonomy.APPLY,
                masteryThreshold: 85,
                optional: false,
            });
        }

        if (message.includes('algorithm') || message.includes('sorting') || message.includes('search')) {
            topics.push({
                title: 'Algorithm Design',
                description: 'Designing and implementing efficient algorithms for problem solving.',
                taxonomy: CompetencyTaxonomy.APPLY,
                masteryThreshold: 90,
                optional: false,
            });
        }

        if (message.includes('database') || message.includes('sql') || message.includes('data')) {
            topics.push({
                title: 'Database Management',
                description: 'Designing, implementing, and querying databases effectively.',
                taxonomy: CompetencyTaxonomy.APPLY,
                masteryThreshold: 80,
                optional: false,
            });
        }

        if (message.includes('web') || message.includes('html') || message.includes('css') || message.includes('javascript')) {
            topics.push({
                title: 'Web Development',
                description: 'Creating responsive and interactive web applications using modern technologies.',
                taxonomy: CompetencyTaxonomy.CREATE,
                masteryThreshold: 85,
                optional: false,
            });
        }

        return topics;
    }

    private generateTitleFromMessage(message: string): string {
        // Simple title generation - take first few meaningful words
        const words = message
            .split(' ')
            .filter((word) => word.length > 2 && !['the', 'and', 'for', 'with', 'can', 'you', 'help', 'create'].includes(word.toLowerCase()))
            .slice(0, 3);

        return words.map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()).join(' ') || 'Course Competency';
    }

    /**
     * Creates the pending competencies in the course
     */
    private createPendingCompetencies(courseId: number): Observable<string> {
        if (this.pendingCompetencies.length === 0) {
            return of('No competencies to create.').pipe(delay(500));
        }

        const count = this.pendingCompetencies.length;
        const competenciesToSave = this.pendingCompetencies.map((draft) => {
            const competency = new Competency();
            competency.title = draft.title;
            competency.description = draft.description;
            competency.taxonomy = draft.taxonomy;
            competency.masteryThreshold = draft.masteryThreshold;
            competency.optional = draft.optional;
            competency.softDueDate = draft.softDueDate;
            return competency;
        });

        this.pendingCompetencies = []; // Clear pending competencies

        return new Observable<string>((subscriber) => {
            this.competencyService.createBulk(competenciesToSave, courseId).subscribe({
                next: () => {
                    const response =
                        `🎉 **Successfully created ${count} competencies for your course!**\n\n` +
                        `The competencies are now available in your course and students can start working towards achieving these learning objectives.\n\n` +
                        `💡 **What's next?**\n` +
                        `• Link competencies to exercises and lectures\n` +
                        `• Set up competency relations if needed\n` +
                        `• Monitor student progress through the competency dashboard\n\n` +
                        `Is there anything else I can help you with?`;

                    subscriber.next(response);
                    subscriber.complete();
                },
                error: () => {
                    // Restore pending competencies if creation failed
                    this.pendingCompetencies = competenciesToSave.map((comp) => ({
                        title: comp.title!,
                        description: comp.description!,
                        taxonomy: comp.taxonomy!,
                        masteryThreshold: comp.masteryThreshold!,
                        optional: comp.optional!,
                        softDueDate: comp.softDueDate,
                    }));

                    subscriber.next(
                        `❌ **Error creating competencies.** I couldn't save the competencies to your course. The competencies are still available for retry. Please try saying "create them" again, or contact your administrator if the problem persists.`,
                    );
                    subscriber.complete();
                },
            });
        });
    }

    private generateGeneralResponse(message: string): Observable<string> {
        const lowerMessage = message.toLowerCase();

        // Check for specific topics and provide relevant responses
        if (lowerMessage.includes('sorting') || lowerMessage.includes('algorithm')) {
            return of(
                'Great! For sorting algorithms, I suggest competencies like: Algorithm Analysis (understanding time/space complexity), Divide & Conquer strategies, and Implementation Skills. Would you like me to create specific competencies for sorting algorithms?',
            ).pipe(delay(1200));
        }

        if (lowerMessage.includes('java') || lowerMessage.includes('programming')) {
            return of(
                'For Java programming, I can help you create competencies covering: Object-Oriented Programming principles, Java syntax and semantics, Exception handling, Collections framework, and best practices. What specific Java topics should we focus on?',
            ).pipe(delay(1200));
        }

        if (lowerMessage.includes('database') || lowerMessage.includes('sql')) {
            return of(
                'For database courses, key competencies include: SQL Query Writing, Database Design principles, Normalization techniques, Transaction management, and Performance optimization. Would you like me to suggest specific database competencies?',
            ).pipe(delay(1200));
        }

        if (lowerMessage.includes('web') || lowerMessage.includes('frontend') || lowerMessage.includes('react')) {
            return of(
                'For web development, I can create competencies for: HTML/CSS fundamentals, JavaScript programming, React framework usage, Responsive design, and API integration. What aspects of web development are most important for your course?',
            ).pipe(delay(1200));
        }

        // Default helpful responses
        const responses = [
            `I'm here to help you with course competencies! You can ask me to:\n\n• **Generate from course description** - "Create competencies from course description"\n• **Create specific competencies** - "Help me create competencies for data structures"\n• **Suggest learning objectives** - "What competencies should I have for a Java course?"\n\nWhat would you like to work on?`,

            `I can assist you with managing competencies for your course. Here are some things I can help with:\n\n📚 **Auto-generate from course description** - I'll analyze your course content\n🎯 **Create custom competencies** for specific topics\n📅 **Set appropriate mastery thresholds** and difficulty levels\n\nJust describe what you'd like to create, and I'll help you build it!`,

            `As your AI competency assistant, I can help you design effective learning objectives for your students. Try asking me something like:\n\n• "Generate competencies from course description"\n• "Create competencies for web development"\n• "I need learning objectives for algorithms"\n\nWhat subject area are you working on?`,
        ];

        const randomResponse = responses[Math.floor(Math.random() * responses.length)];
        return of(randomResponse).pipe(delay(1200));
    }

    private getCompetencyPrompt(): string {
        return (
            `I'd love to help you create competencies! You have two options:\n\n` +
            `🤖 **Auto-generate from course description:**\n` +
            `Say "Generate competencies from course description" and I'll analyze your course content automatically.\n\n` +
            `📝 **Create custom competencies:**\n` +
            `Tell me what topics you'd like to cover and I'll create specific competencies.\n\n` +
            `**Examples:**\n` +
            `• "Generate competencies from course description"\n` +
            `• "Create competencies for Java programming"\n` +
            `• "Help me with data structures and algorithms"\n` +
            `• "I need competencies for web development"\n\n` +
            `What would you like to focus on?`
        );
    }

    private getTaxonomyLabel(taxonomy: CompetencyTaxonomy): string {
        const labels = {
            [CompetencyTaxonomy.REMEMBER]: 'Remember',
            [CompetencyTaxonomy.UNDERSTAND]: 'Understand',
            [CompetencyTaxonomy.APPLY]: 'Apply',
            [CompetencyTaxonomy.ANALYZE]: 'Analyze',
            [CompetencyTaxonomy.EVALUATE]: 'Evaluate',
            [CompetencyTaxonomy.CREATE]: 'Create',
        };
        return labels[taxonomy];
    }
}
