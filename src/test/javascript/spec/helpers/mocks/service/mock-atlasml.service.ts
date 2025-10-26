import { Injectable } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';

/**
 * Mock service for AtlasML API calls used in testing.
 * Provides configurable responses for different testing scenarios.
 */
@Injectable()
export class MockAtlasMLService {
    private mockResponses: Map<string, any> = new Map();
    private shouldError = false;
    private errorResponse: any = { status: 500, message: 'Mock error' };
    private responseDelay = 0;

    /**
     * Configure mock response for a specific request
     */
    setMockResponse(description: string, response: any): void {
        this.mockResponses.set(description, response);
    }

    /**
     * Configure default mock response for all requests
     */
    setDefaultMockResponse(response: any): void {
        this.setMockResponse('default', response);
    }

    /**
     * Configure service to return errors
     */
    setShouldError(shouldError: boolean, errorResponse?: any): void {
        this.shouldError = shouldError;
        if (errorResponse) {
            this.errorResponse = errorResponse;
        }
    }

    /**
     * Configure response delay for testing async behavior
     */
    setResponseDelay(delayMs: number): void {
        this.responseDelay = delayMs;
    }

    /**
     * Mock implementation of competency suggestion API
     */
    suggestCompetencies(request: { description: string; course_id: string }): Observable<any> {
        if (this.shouldError) {
            const error$ = throwError(() => this.errorResponse);
            return this.responseDelay > 0 ? error$.pipe(delay(this.responseDelay)) : error$;
        }

        let response = this.mockResponses.get(request.description) || this.mockResponses.get('default');

        if (!response) {
            // Default response based on description keywords
            response = this.generateDefaultResponse(request.description);
        }

        const response$ = of(response);
        return this.responseDelay > 0 ? response$.pipe(delay(this.responseDelay)) : response$;
    }

    /**
     * Reset all mock configurations
     */
    reset(): void {
        this.mockResponses.clear();
        this.shouldError = false;
        this.errorResponse = { status: 500, message: 'Mock error' };
        this.responseDelay = 0;
    }

    /**
     * Generate realistic responses based on description content
     */
    private generateDefaultResponse(description: string): any {
        const lowerDescription = description.toLowerCase();
        const suggestions: any[] = [];

        // Simple keyword matching for realistic suggestions
        if (lowerDescription.includes('program') || lowerDescription.includes('code') || lowerDescription.includes('implement')) {
            suggestions.push({ id: 1, title: 'Programming Fundamentals' });
        }

        if (lowerDescription.includes('data structure') || lowerDescription.includes('array') || lowerDescription.includes('tree') || lowerDescription.includes('hash')) {
            suggestions.push({ id: 2, title: 'Data Structures' });
        }

        if (lowerDescription.includes('algorithm') || lowerDescription.includes('sort') || lowerDescription.includes('search')) {
            suggestions.push({ id: 3, title: 'Algorithms' });
        }

        if (lowerDescription.includes('object') || lowerDescription.includes('class') || lowerDescription.includes('oop')) {
            suggestions.push({ id: 4, title: 'Object-Oriented Programming' });
        }

        if (lowerDescription.includes('database') || lowerDescription.includes('sql') || lowerDescription.includes('table')) {
            suggestions.push({ id: 5, title: 'Database Design' });
        }

        return { competencies: suggestions };
    }
}

/**
 * Mock factory for creating AtlasML service instances in tests
 */
export class MockAtlasMLServiceFactory {
    static createWithSuggestions(suggestions: Array<{ id: number; title: string }>): MockAtlasMLService {
        const service = new MockAtlasMLService();
        service.setDefaultMockResponse({ competencies: suggestions });
        return service;
    }

    static createWithError(errorResponse: any = { status: 500 }): MockAtlasMLService {
        const service = new MockAtlasMLService();
        service.setShouldError(true, errorResponse);
        return service;
    }

    static createWithDelay(delayMs: number, response?: any): MockAtlasMLService {
        const service = new MockAtlasMLService();
        service.setResponseDelay(delayMs);
        if (response) {
            service.setDefaultMockResponse(response);
        }
        return service;
    }
}

/**
 * Common test data for AtlasML tests
 */
export const AtlasMLTestData = {
    sampleCompetencies: [
        { id: 1, title: 'Programming Fundamentals', description: 'Basic programming concepts' },
        { id: 2, title: 'Data Structures', description: 'Arrays, lists, trees, graphs' },
        { id: 3, title: 'Algorithms', description: 'Sorting, searching, optimization' },
        { id: 4, title: 'Object-Oriented Programming', description: 'Classes, inheritance, polymorphism' },
        { id: 5, title: 'Database Design', description: 'Relational databases, normalization' },
    ],

    exerciseDescriptions: {
        programming: 'Create a Java program that implements a binary search tree with insertion and deletion operations',
        algorithms: 'Write an algorithm to find the shortest path between two nodes in a weighted graph',
        dataStructures: 'Implement a hash table with collision resolution using chaining method',
        oop: 'Design a class hierarchy for a library management system using inheritance and polymorphism',
        database: 'Create a normalized database schema for an e-commerce application',
        mixed: 'Develop a web application that uses object-oriented design patterns to implement a search algorithm over a database',
    },

    apiResponses: {
        programming: {
            competencies: [
                { id: 1, title: 'Programming Fundamentals' },
                { id: 4, title: 'Object-Oriented Programming' },
            ],
        },
        algorithms: {
            competencies: [
                { id: 3, title: 'Algorithms' },
                { id: 2, title: 'Data Structures' },
            ],
        },
        dataStructures: {
            competencies: [
                { id: 2, title: 'Data Structures' },
                { id: 1, title: 'Programming Fundamentals' },
            ],
        },
        oop: { competencies: [{ id: 4, title: 'Object-Oriented Programming' }] },
        database: { competencies: [{ id: 5, title: 'Database Design' }] },
        empty: { competencies: [] },
        invalid: { invalid: 'response' },
    },
};
