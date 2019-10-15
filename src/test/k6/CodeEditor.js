import { login } from './requests/requests.js';
import { startExercise } from "./requests/programmingExercise.js";
import { group, sleep } from 'k6';

// Version: 1.1
// Creator: Firefox
// Browser: Firefox

export let options = {
    maxRedirects: 0,
    iterations: 100,
    vus: 100,
    rps: 5
};

export default function() {
  let websocketConnectionTime = 2000; // Time in seconds the websocket is kept open, if set to 0 no websocket connection is estahblished

  let username = __ENV.BASE_USERNAME; // USERID gets replaced with a random number between 1 and maxTestUser
  let password = __ENV.BASE_PASSWORD; // USERID gets replaced with a random number between 1 and maxTestUser

  let courseId = 10; // id of the course where the exercise is located
  let exerciseId = 717; // id of the programming-exercise, code-editor must be enabled

  // Delay so that not all users start at the same time, batches of 3 users per second
  sleep(Math.floor(__VU / 3));

	group('Artemis Programming Exercise Participation Loadtest', function() {
      // The user is randomly selected
      let userId = __VU; // Math.floor((Math.random() * maxTestUser)) + 1;
      let currentUsername = username.replace('USERID', userId);
      let currentPassword = password.replace('USERID', userId);
      let artemis = login(currentUsername, currentPassword);

      // Start exercise
      let participationId = startExercise(artemis, courseId, exerciseId);

      // Initiate websocket connection if connection time is set to value greater than 0
      if (websocketConnectionTime > 0) {
        if (participationId) {
            artemis.simulateSubmissionChanges(exerciseId, participationId, websocketConnectionTime);
        }
        sleep(websocketConnectionTime);
      }
	});

}
