import { fail } from 'k6';
import { nextAlphanumeric } from '../util/utils.js';
import { ASSESS_MODELING_SUBMISSION, SUBMIT_MODELING_EXAM, MODELING_EXERCISES } from './endpoints.js';

export function submitRandomModelingAnswerExam(artemis, exercise, submissionId, participation) {
    const answer = {
        id: submissionId,
        isSynced: false,
        submissionExerciseType: 'modeling',
        submitted: true,
        model: '{"version":"2.0.0","type":"ClassDiagram","size":{"width":626,"height":578},"interactive":{"elements":[],"relationships":[]},"elements":[{"id":"35f037f7-0606-4798-b43a-9bc76d741421","name":"Package","type":"Package","owner":null,"bounds":{"x":348,"y":40,"width":200,"height":100}},{"id":"15ac99ee-292b-4a43-8e7f-47820843d132","name":"Abstract","type":"AbstractClass","owner":null,"bounds":{"x":342,"y":271,"width":200,"height":110},"attributes":["d29384ec-ffaa-4deb-812d-d18784074dbf"],"methods":["eb8ae77d-ee16-4f67-ba16-272caa3d56e1"]},{"id":"d29384ec-ffaa-4deb-812d-d18784074dbf","name":"+ attribute: Type","type":"ClassAttribute","owner":"15ac99ee-292b-4a43-8e7f-47820843d132","bounds":{"x":342,"y":321,"width":200,"height":30}},{"id":"eb8ae77d-ee16-4f67-ba16-272caa3d56e1","name":"+ method()","type":"ClassMethod","owner":"15ac99ee-292b-4a43-8e7f-47820843d132","bounds":{"x":342,"y":351,"width":200,"height":30}},{"id":"ac3a15e7-582b-4366-a350-3c4adc529e69","name":"Package","type":"Package","owner":null,"bounds":{"x":0,"y":66,"width":200,"height":100}}],"relationships":[{"id":"5fef0380-c219-413f-a6f9-e5fbac190840","name":"","type":"ClassBidirectional","owner":null,"bounds":{"x":442,"y":0,"width":146,"height":271},"path":[{"x":0,"y":271},{"x":0,"y":231},{"x":146,"y":231},{"x":146,"y":0},{"x":6,"y":0},{"x":6,"y":40}],"source":{"direction":"Up","element":"15ac99ee-292b-4a43-8e7f-47820843d132","multiplicity":"","role":""},"target":{"direction":"Up","element":"35f037f7-0606-4798-b43a-9bc76d741421","multiplicity":"","role":""}},{"id":"a3416bb8-f2c1-43ed-8e09-6f15270425ba","name":"","type":"ClassBidirectional","owner":null,"bounds":{"x":200,"y":0,"width":248,"height":116},"path":[{"x":0,"y":116},{"x":40,"y":116},{"x":40,"y":0},{"x":248,"y":0},{"x":248,"y":40}],"source":{"direction":"Right","element":"ac3a15e7-582b-4366-a350-3c4adc529e69","multiplicity":"","role":""},"target":{"direction":"Up","element":"35f037f7-0606-4798-b43a-9bc76d741421","multiplicity":"","role":""}}],"assessments":[]}',
    };

    if (participation) {
        answer.participation = participation;
    }

    let res = artemis.put(SUBMIT_MODELING_EXAM(exercise.id), answer);
    if (res[0].status !== 200) {
        console.log('ERROR when submitting modeling (Exam) via REST. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('FAILTEST: Could not submit modeling (Exam) via REST (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    return answer;
}

export function newModelingExercise(artemis, exerciseGroup, courseId) {
    const exercise = {
        maxPoints: 1,
        title: 'Modeling K6 ' + nextAlphanumeric(5),
        type: 'modeling',
        mode: 'INDIVIDUAL',
        assessmentType: 'SEMI_AUTOMATIC',
        diagramType: 'ClassDiagram',
    };

    if (courseId) {
        exercise.course = { id: courseId };
    }

    if (exerciseGroup) {
        exercise.exerciseGroup = exerciseGroup;
    }

    const res = artemis.post(MODELING_EXERCISES, exercise);
    if (res[0].status !== 201) {
        console.log('ERROR when creating a new modeling exercise. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('FAILTEST: Could not create modeling exercise (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    console.log('SUCCESS: Generated new modeling exercise');

    return JSON.parse(res[0].body);
}

export function updateModelingExerciseDueDate(artemis, exercise) {
    const currentDate = new Date();

    const updateExercise = Object.assign({}, exercise);
    updateExercise.dueDate = new Date(currentDate.getTime() + 10000); // Visible in 1 minutes

    const res = artemis.put(MODELING_EXERCISES, updateExercise);
    console.log(res);
    if (res[0].status !== 200) {
        console.log('ERROR when updating the modeling exercise. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('FAILTEST: Could not create modeling exercise (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    console.log('SUCCESS: Generated new modeling exercise');

    return JSON.parse(res[0].body);
}

export function assessModelingSubmission(artemis, submissionId, resultId) {
    const assessment = [
        {
            credits: 4,
            reference: 'Package:35f037f7-0606-4798-b43a-9bc76d741421',
            referenceId: '35f037f7-0606-4798-b43a-9bc76d741421',
            referenceType: 'Package',
            text: 'AssessmentText',
        },
    ];
    let res = artemis.put(ASSESS_MODELING_SUBMISSION(submissionId, resultId), assessment);
    if (res[0].status !== 200) {
        console.log('ERROR when assessing modeling (Exercise) via REST. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('FAILTEST: Could not assess modeling (Exercise) via REST (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    return assessment;
}
