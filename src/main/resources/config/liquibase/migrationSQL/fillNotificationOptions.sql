CREATE TABLE notification_option (
     ID      BIGINT       NOT NULL,
     TYPE    VARCHAR(255) NOT NULL,
     APP     BIT(1)       NOT NULL,
     EMAIl   BIT(1)       NOT NULL,
     USER_ID BIGINT       NOT NULL,
     PRIMARY KEY (ID),
     FOREIGN KEY (USER_ID)
         REFERENCES jhi_user(ID)
         ON DELETE CASCADE
);

INSERT INTO notification_option(ID, TYPE, APP, EMAIl, USER_ID)
VALUES (27, 'QUIZ_EXERCISE_STARTED', 1, 0, 2);

-- @TODO Replace this code with actual script that will be needed for production ~fillParticipationScore.sql
/*
DROP TABLE artemis.notification_option;

SELECT * FROM artemis.notification_option;

CREATE TABLE artemis.notification_option (
   ID      BIGINT       NOT NULL,
   TYPE    VARCHAR(255) NOT NULL,
   APP     BIT(1)       NOT NULL,
   EMAIl   BIT(1)       NOT NULL,
   USER_ID BIGINT       NOT NULL,
   PRIMARY KEY (ID),
   FOREIGN KEY (USER_ID)
       REFERENCES jhi_user(ID)
       ON DELETE CASCADE
);

SELECT id FROM artemis.jhi_user WHERE login = 'potter';

INSERT INTO artemis.notification_option(ID, TYPE, APP, EMAIl, USER_ID)
VALUES (27, 'QUIZ_EXERCISE_STARTED', 1, 0, 2);
*/
