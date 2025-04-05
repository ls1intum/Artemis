// src/test/javascript/arch/architecture.test.ts
import { filesOfProject } from 'tsarch';

describe('Angular Architecture Rules', () => {
    // Define module patterns
    const regularModules = ['assessment', 'atlas', 'exam', 'fileupload', 'iris', 'lecture', 'lti', 'modeling', 'plagiarism', 'programming', 'quiz', 'text', 'tutorialgroup'];

    const exceptionModules = ['core', 'exercise', 'shared', 'buildagent', 'communication'];

    const allModules = [...regularModules, ...exceptionModules];

    // Modules that are allowed to access the exercise module
    const modulesWithExerciseAccess = ['fileupload', 'quiz', 'programming', 'text', 'modeling'];

    // Define the base path
    const basePath = 'src/main/webapp/app';

    jest.setTimeout(60000);

    // NOTE: We cannot use inFolder below due to https://github.com/ts-arch/ts-arch/issues/82 and have to use matchingPattern instead
    describe('Module Structure Rules', () => {
        it('modules should be free of cycles', async () => {
            for (const module of allModules) {
                await filesOfProject().inFolder(`${basePath}/${module}`).should().beFreeOfCycles().check();
            }
        });
    });

    describe('Dependency Rules', () => {
        it('overview should not depend on manage', async () => {
            for (const module of regularModules) {
                await filesOfProject().inFolder(`${basePath}/${module}/overview`).shouldNot().dependOnFiles().inFolder(`${basePath}/${module}/overview`).check();
            }
        });

        it('manage should not depend on overview', async () => {
            for (const module of regularModules) {
                await filesOfProject().matchingPattern(`${basePath}/${module}/manage/**/*`).shouldNot().dependOnFiles().inFolder(`${basePath}/${module}/overview`).check();
            }
        });

        it('no module should depend on another module (except shared and possibly exercise)', async () => {
            // Test that each module only depends on its own content or potentially shared/exercise
            for (const sourceModule of allModules) {
                for (const targetModule of allModules) {
                    // Skip checking dependencies on self, shared, and exercise (for specific modules)
                    if (sourceModule === targetModule || targetModule === 'shared') {
                        continue;
                    }

                    // Special case for modules allowed to access exercise
                    if (targetModule === 'exercise' && modulesWithExerciseAccess.includes(sourceModule)) {
                        continue;
                    }

                    // All other cross-module dependencies are forbidden
                    await filesOfProject().inFolder(`${basePath}/${sourceModule}`).shouldNot().dependOnFiles().inFolder(`${basePath}/${targetModule}`).check();
                }
            }
        });

        // Additional test that checks for dependencies on specific module sections
        it('modules should not depend on specific components of other modules (including irregular modules)', async () => {
            for (const sourceModule of allModules) {
                for (const targetModule of allModules) {
                    // Skip self-dependencies
                    if (sourceModule === targetModule) {
                        continue;
                    }

                    // Special case for modules allowed to access exercise
                    if (targetModule === 'exercise' && modulesWithExerciseAccess.includes(sourceModule)) {
                        continue;
                    }

                    // Skip shared module since it's designed to be shared
                    if (targetModule === 'shared') {
                        continue;
                    }

                    // For regular modules, we already checked manage/overview specifically
                    // Now check for dependencies on any specific folders/files in target modules
                    // This will capture dependencies on irregular structure components too
                    await filesOfProject().inFolder(`${basePath}/${sourceModule}`).shouldNot().dependOnFiles().inFolder(`${basePath}/${targetModule}`).check();
                }
            }
        });

        // Test shared module access permissions
        it('shared module components should be properly isolated', async () => {
            for (const module of allModules) {
                // Skip the shared module itself
                if (module === 'shared') {
                    continue;
                }

                // Only shared components from a module should be accessed by other modules
                for (const otherModule of allModules) {
                    if (module === otherModule) {
                        continue;
                    }

                    // Other modules should only access the shared directory of this module
                    await filesOfProject().inFolder(`${basePath}/${otherModule}`).shouldNot().dependOnFiles().inFolder(`${basePath}/${module}`).check();
                }
            }
        });
    });
});
