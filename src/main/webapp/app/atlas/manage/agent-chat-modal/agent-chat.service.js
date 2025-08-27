import { __decorate } from 'tslib';
import { Injectable } from '@angular/core';
import { of, delay } from 'rxjs';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import dayjs from 'dayjs/esm';
let AgentChatService = class AgentChatService {
    pendingCompetencies = [];
    sendMessage(message, courseId) {
        const lowerMessage = message.toLowerCase();
        if (lowerMessage.includes('yes') || lowerMessage.includes('create') || lowerMessage.includes('confirm')) {
            if (this.pendingCompetencies.length > 0) {
                return this.createPendingCompetencies(courseId);
            }
        }
        if (this.isCompetencyRequest(lowerMessage)) {
            return this.sendCompetencySuggestionRequest(message, courseId);
        }
        return this.sendChatRequest(message, courseId);
    }
    sendChatRequest(message, courseId) {
        return this.generateGeneralResponse(message).pipe(delay(800));
    }
    sendCompetencySuggestionRequest(message, courseId) {
        return this.generateCompetencyResponse(message, courseId);
    }
    convertToCompetencyDrafts(competencies) {
        return competencies.map((comp) => ({
            title: comp.title || comp.name,
            description: comp.description,
            taxonomy: this.mapToTaxonomy(comp.taxonomy),
            masteryThreshold: comp.masteryThreshold || 85,
            optional: comp.optional || false,
            softDueDate: dayjs().add(2, 'weeks'),
        }));
    }
    mapToTaxonomy(taxonomyString) {
        if (!taxonomyString) return CompetencyTaxonomy.UNDERSTAND;
        const upper = taxonomyString.toUpperCase();
        if (upper.includes('REMEMBER')) return CompetencyTaxonomy.REMEMBER;
        if (upper.includes('UNDERSTAND')) return CompetencyTaxonomy.UNDERSTAND;
        if (upper.includes('APPLY')) return CompetencyTaxonomy.APPLY;
        if (upper.includes('ANALYZE')) return CompetencyTaxonomy.ANALYZE;
        if (upper.includes('EVALUATE')) return CompetencyTaxonomy.EVALUATE;
        if (upper.includes('CREATE')) return CompetencyTaxonomy.CREATE;
        return CompetencyTaxonomy.UNDERSTAND;
    }
    isCompetencyRequest(message) {
        const competencyKeywords = ['competenc', 'learning objective', 'skill', 'knowledge', 'create', 'generate', 'suggest', 'help me with', 'course', 'topic', 'subject'];
        return competencyKeywords.some((keyword) => message.includes(keyword));
    }
    generateCompetencyResponse(message, courseId) {
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
    parseCompetencyRequest(message) {
        const competencies = [];
        const lowerMessage = message.toLowerCase();
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
    extractTopics(message) {
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
    generateTitleFromMessage(message) {
        const words = message
            .split(' ')
            .filter((word) => word.length > 2 && !['the', 'and', 'for', 'with', 'can', 'you', 'help', 'create'].includes(word.toLowerCase()))
            .slice(0, 3);
        return words.map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()).join(' ') || 'Course Competency';
    }
    createPendingCompetencies(courseId) {
        if (this.pendingCompetencies.length === 0) {
            return of('No competencies to create.').pipe(delay(500));
        }
        const count = this.pendingCompetencies.length;
        this.pendingCompetencies = [];
        const response =
            `🎉 **Mock: Created ${count} competencies for your course!**\n\n` +
            `(This is a demo - competencies would be created in the actual implementation)\n\n` +
            `The competencies have been simulated and would now be available in your course. ` +
            `Students would be able to start working towards achieving these learning objectives.\n\n` +
            `💡 **What's next?**\n` +
            `• Link competencies to exercises and lectures\n` +
            `• Set up competency relations if needed\n` +
            `• Monitor student progress\n\n` +
            `Is there anything else I can help you with?`;
        return of(response).pipe(delay(1000));
    }
    generateGeneralResponse(message) {
        const lowerMessage = message.toLowerCase();
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
        const responses = [
            `I'm here to help you with course competencies! You can ask me to:\n\n• **Create competencies** - "Help me create competencies for data structures"\n• **Suggest learning objectives** - "What competencies should I have for a Java course?"\n• **Generate course content** - "Create competencies for machine learning"\n\nWhat would you like to work on?`,
            `I can assist you with managing competencies for your course. Here are some things I can help with:\n\n📚 **Generate competencies** based on course topics\n🎯 **Set appropriate learning objectives** and mastery thresholds\n📅 **Suggest due dates** and difficulty levels\n\nJust describe what you'd like to create, and I'll help you build it!`,
            `As your AI competency assistant, I can help you design effective learning objectives for your students. Try asking me something like:\n\n• "Create competencies for web development"\n• "I need learning objectives for algorithms"\n• "Help me with database course competencies"\n\nWhat subject area are you working on?`,
        ];
        const randomResponse = responses[Math.floor(Math.random() * responses.length)];
        return of(randomResponse).pipe(delay(1200));
    }
    getCompetencyPrompt() {
        return (
            `I'd love to help you create competencies! To get started, please tell me:\n\n` +
            `📋 **What topic or subject** should the competencies cover?\n` +
            `🎓 **What should students learn** or be able to do?\n` +
            `📚 **Any specific skills** or concepts to include?\n\n` +
            `For example:\n` +
            `• "Create competencies for Java programming"\n` +
            `• "Help me with data structures and algorithms"\n` +
            `• "I need competencies for web development"\n\n` +
            `What would you like to focus on?`
        );
    }
    getTaxonomyLabel(taxonomy) {
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
};
AgentChatService = __decorate(
    [
        Injectable({
            providedIn: 'root',
        }),
    ],
    AgentChatService,
);
export { AgentChatService };
//# sourceMappingURL=agent-chat.service.js.map
