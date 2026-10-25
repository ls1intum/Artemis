import { describe, expect, it } from 'vitest';
import { Course } from './course.model';
import { toCourseCreateDTO, toCourseUpdateDTO } from './course-update-dto.model';

describe('Course welcome context mappings', () => {
    it('carries teaching context in both create and update requests and preserves an intentional clear', () => {
        const course = new Course();
        course.id = 42;
        course.title = 'Algorithms';
        course.shortName = 'algo';
        course.courseConfiguration = { studentWelcomeContext: 'Learn through practice.', gradeRelevant: false, autoOrchestratorEnabled: true };
        expect(toCourseCreateDTO(course).studentWelcomeContext).toBe('Learn through practice.');
        expect(toCourseUpdateDTO(course).studentWelcomeContext).toBe('Learn through practice.');
        course.courseConfiguration.studentWelcomeContext = '';
        expect(toCourseUpdateDTO(course).studentWelcomeContext).toBe('');
        expect(toCourseUpdateDTO(course).autoOrchestratorEnabled).toBe(true);
        expect(toCourseUpdateDTO(course).gradeRelevant).toBe(false);
    });
});
