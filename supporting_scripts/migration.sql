-- Update quiz_question table for multiple-choice questions.
UPDATE quiz_question q
SET q.content = (
    -- Select JSON array of answer options and their details.
    SELECT JSON_ARRAYAGG(
               JSON_OBJECT(
                   'id', ao.id,
                   'text', ao.text,
                   'hint', ao.hint,
                   'explanation', ao.explanation,
                   'isCorrect', ao.is_correct,
                   'invalid', ao.invalid
               )
           )
    FROM answer_option ao
    WHERE ao.question_id = q.id
)
WHERE q.discriminator = 'MC'; -- Only update multiple-choice questions.


-- Update quiz_question table for short answer questions.
UPDATE quiz_question q
SET q.content = (
    -- Select JSON object containing spots, solutions, and correct mappings.
    SELECT JSON_OBJECT(
               'spots', (
            -- Select JSON array of short answer spots and their details.
            SELECT JSON_ARRAYAGG(
                       JSON_OBJECT(
                           'id', sas.id,
                           'invalid', sas.invalid,
                           'width', sas.width,
                           'spotNr', sas.spot_nr
                       )
                   )
            FROM short_answer_spot sas
            WHERE sas.question_id = q.id AND q.discriminator = 'SA'
        ),
               'solutions', (
                   -- Select JSON array of short answer solutions and their details.
                   SELECT JSON_ARRAYAGG(
                              JSON_OBJECT(
                                  'id', saso.id,
                                  'invalid', saso.invalid,
                                  'text', saso.text
                              )
                          )
                   FROM short_answer_solution saso
                   WHERE saso.question_id = q.id AND q.discriminator = 'SA'
               ),
               'correctMappings', (
                   -- Select JSON array of correct mappings between spots and solutions.
                   SELECT JSON_ARRAYAGG(
                              JSON_OBJECT(
                                  'id', sam.id,
                                  'spot', (
                                      -- Select JSON object for spot details.
                                      SELECT JSON_OBJECT(
                                                 'id', sas.id,
                                                 'invalid', sas.invalid,
                                                 'width', sas.width,
                                                 'spotNr', sas.spot_nr
                                             )
                                      FROM short_answer_spot sas
                                      WHERE sas.id = sam.spot_id
                                  ),
                                  'invalid', sam.invalid,
                                  'solution', (
                                      -- Select JSON object for solution details.
                                      SELECT JSON_OBJECT(
                                                 'id', saso.id,
                                                 'invalid', saso.invalid,
                                                 'text', saso.text
                                             )
                                      FROM short_answer_solution saso
                                      WHERE saso.id = sam.solution_id
                                  ),
                                  'shortAnswerSpotIndex', sam.short_answer_spot_index,
                                  'shortAnswerSolutionIndex', sam.short_answer_solution_index
                              )
                          )
                   FROM short_answer_mapping sam
                   WHERE sam.question_id = q.id
               )
           )
)
WHERE q.discriminator = 'SA'; -- Only update short answer questions.


-- Update quiz_question table for drag-and-drop questions.
UPDATE quiz_question q
SET q.content = (
    -- Select JSON object containing drop locations, drag items, and correct mappings.
    SELECT JSON_OBJECT(
               'dropLocations', (
            -- Select JSON array of drop locations and their details.
            SELECT JSON_ARRAYAGG(
                       JSON_OBJECT(
                           'id', dl.id,
                           'posX', dl.pos_x,
                           'posY', dl.pos_y,
                           'width', dl.width,
                           'height', dl.height
                       )
                   )
            FROM drop_location dl
            WHERE dl.question_id = q.id AND q.discriminator = 'DD'
        ),
               'dragItems', (
                   -- Select JSON array of drag items and their details.
                   SELECT JSON_ARRAYAGG(
                              JSON_OBJECT(
                                  'id', di.id,
                                  'invalid', di.invalid,
                                  'text', di.text,
                                  'pictureFilePath', di.picture_file_path
                              )
                          )
                   FROM drag_item di
                   WHERE di.question_id = q.id AND q.discriminator = 'DD'
               ),
               'correctMappings', (
                   -- Select JSON array of correct mappings between drag items and drop locations.
                   SELECT JSON_ARRAYAGG(
                              JSON_OBJECT(
                                  'id', ddm.id,
                                  'dragItem', (
                                      -- Select JSON object for drag item details.
                                      SELECT JSON_OBJECT(
                                                 'id', di.id,
                                                 'invalid', di.invalid,
                                                 'text', di.text,
                                                 'pictureFilePath', di.picture_file_path
                                             )
                                      FROM drag_item di
                                      WHERE di.id = ddm.drag_item_id
                                  ),
                                  'invalid', ddm.invalid,
                                  'dropLocation', (
                                      -- Select JSON object for drop location details.
                                      SELECT JSON_OBJECT(
                                                 'id', dl.id,
                                                 'posX', dl.pos_x,
                                                 'posY', dl.pos_y,
                                                 'width', dl.width,
                                                 'height', dl.height
                                             )
                                      FROM drop_location dl
                                      WHERE dl.id = ddm.drop_location_id
                                  ),
                                  'dragItemIndex', ddm.drag_item_index,
                                  'dropLocationIndex', ddm.drop_location_index
                              )
                          )
                   FROM drag_and_drop_mapping ddm
                   WHERE ddm.question_id = q.id
               )
           )
)
WHERE q.discriminator = 'DD'; -- Only update drag-and-drop questions.


-- Update submitted_answer table for multiple-choice questions.
UPDATE submitted_answer s
SET s.selection = (
    -- Select JSON array of selected answer options and their details.
    SELECT JSON_ARRAYAGG(
               JSON_OBJECT(
                   'id', ao.id,
                   'text', ao.text,
                   'hint', ao.hint,
                   'explanation', ao.explanation,
                   'isCorrect', ao.is_correct,
                   'invalid', ao.invalid
               )
           )
    FROM answer_option ao
    WHERE ao.question_id = s.quiz_question_id
)
WHERE s.discriminator = 'MC'; -- Only update multiple-choice answers.


-- Update submitted_answer table for short answer questions.
UPDATE submitted_answer s
SET s.selection = (
    -- Select JSON array of submitted short answer texts and their details.
    SELECT JSON_ARRAYAGG(
               JSON_OBJECT(
                   'id', sast.id,
                   'text', sast.text,
                   'isCorrect', sast.is_correct,
                   'spot', (
                       -- Select JSON object for spot details.
                       SELECT JSON_OBJECT(
                                  'id', sas.id,
                                  'invalid', sas.invalid,
                                  'width', sas.width,
                                  'spotNr', sas.spot_nr
                              )
                       FROM short_answer_spot sas
                       WHERE sas.id = sast.spot_id
                   )
               )
           )
    FROM short_answer_submitted_text sast
    WHERE sast.submitted_answer_id = s.id
)
WHERE s.discriminator = 'SA'; -- Only update short answer answers.


-- Update submitted_answer table for drag-and-drop questions.
UPDATE submitted_answer s
SET s.selection = (
    -- Select JSON array of submitted drag-and-drop mappings and their details.
    SELECT JSON_ARRAYAGG(
               JSON_OBJECT(
                   'id', ddm.id,
                   'dragItemIndex', ddm.drag_item_index,
                   'dropLocationIndex', ddm.drop_location_index,
                   'invalid', ddm.invalid,
                   'dragItem', (
                       -- Select JSON object for drag item details.
                       SELECT JSON_OBJECT(
                                  'id', di.id,
                                  'invalid', di.invalid,
                                  'text', di.text,
                                  'pictureFilePath', di.picture_file_path
                              )
                       FROM drag_item di
                       WHERE di.id = ddm.drag_item_id
                   ),
                   'dropLocation', (
                       -- Select JSON object for drop location details.
                       SELECT JSON_OBJECT(
                                  'id', dl.id,
                                  'posX', dl.pos_x,
                                  'posY', dl.pos_y,
                                  'width', dl.width,
                                  'height', dl.height
                              )
                       FROM drop_location dl
                       WHERE dl.id = ddm.drop_location_id
                   )
               )
           )
    FROM drag_and_drop_mapping ddm
    WHERE ddm.submitted_answer_id = s.id
)
WHERE s.discriminator = 'DD'; -- Only update drag-and-drop answers.


-- Update quiz_question table for multiple-choice questions with statistics.
UPDATE quiz_question q
SET q.statistics = (
    -- Select JSON object containing question type, answer counters, and participant data.
    SELECT JSON_OBJECT(
               'type', 'multiple-choice',
               'answerCounters', (
                   -- Select JSON array of answer counters with detailed answers.
                   SELECT JSON_ARRAYAGG(
                              JSON_OBJECT(
                                  'id', qsc.id,
                                  'ratedCounter', qsc.rated_counter,
                                  'unratedCounter', qsc.un_rated_counter,
                                  'answer', (
                                      -- Select JSON object for detailed answer.
                                      SELECT JSON_OBJECT(
                                                 'id', ao.id,
                                                 'text', ao.text,
                                                 'hint', ao.hint,
                                                 'explanation', ao.explanation,
                                                 'isCorrect', ao.is_correct,
                                                 'invalid', ao.invalid
                                             )
                                      FROM answer_option ao
                                      WHERE ao.id = qsc.answer_id
                                  )
                              )
                          )
                   FROM quiz_statistic_counter qsc
                   WHERE qs.id = qsc.multiple_choice_question_statistic_id
               ),
               'participantsRated', qs.participants_rated,
               'participantsUnrated', qs.participants_unrated,
               'ratedCorrectCounter', qs.rated_correct_counter,
               'unratedCorrectCounter', qs.un_rated_correct_counter
           )
    FROM quiz_statistic qs
    WHERE qs.id = q.quiz_question_statistic_id AND q.discriminator = 'MC'
)
WHERE q.discriminator = 'MC'; -- Only update multiple-choice questions.


-- Update quiz_question table for short answer questions with statistics.
UPDATE quiz_question q
SET q.statistics = (
    -- Select JSON object containing question type, short answer spot counters, and participant data.
    SELECT JSON_OBJECT(
               'type', 'short-answer',
               'shortAnswerSpotCounters', (
                   -- Select JSON array of short answer spot counters with detailed spots.
                   SELECT JSON_ARRAYAGG(
                              JSON_OBJECT(
                                  'id', qsc.id,
                                  'ratedCounter', qsc.rated_counter,
                                  'unratedCounter', qsc.un_rated_counter,
                                  'spot', (
                                      -- Select JSON object for detailed spot.
                                      SELECT JSON_OBJECT(
                                                 'id', sas.id,
                                                 'invalid', sas.invalid,
                                                 'width', sas.width,
                                                 'spotNr', sas.spot_nr
                                             )
                                      FROM short_answer_spot sas
                                      WHERE sas.id = qsc.spot_id
                                  )
                              )
                          )
                   FROM quiz_statistic_counter qsc
                   WHERE qs.id = qsc.short_answer_question_statistic_id
               ),
               'participantsRated', qs.participants_rated,
               'participantsUnrated', qs.participants_unrated,
               'ratedCorrectCounter', qs.rated_correct_counter,
               'unratedCorrectCounter', qs.un_rated_correct_counter
           )
    FROM quiz_statistic qs
    WHERE qs.id = q.quiz_question_statistic_id AND q.discriminator = 'SA'
)
WHERE q.discriminator = 'SA'; -- Only update short answer questions.


-- Update quiz_question table for drag-and-drop questions with statistics.
UPDATE quiz_question q
SET q.statistics = (
    -- Select JSON object containing question type, drop location counters, and participant data.
    SELECT JSON_OBJECT(
               'type', 'drag-and-drop',
               'dropLocationCounters', (
                   -- Select JSON array of drop location counters with detailed locations.
                   SELECT JSON_ARRAYAGG(
                              JSON_OBJECT(
                                  'id', qsc.id,
                                  'ratedCounter', qsc.rated_counter,
                                  'unratedCounter', qsc.un_rated_counter,
                                  'dropLocation', (
                                      -- Select JSON object for detailed drop location.
                                      SELECT JSON_OBJECT(
                                                 'id', dl.id,
                                                 'posX', dl.pos_x,
                                                 'posY', dl.pos_y,
                                                 'width', dl.width,
                                                 'height', dl.height
                                             )
                                      FROM drop_location dl
                                      WHERE dl.id = qsc.drop_location_id
                                  )
                              )
                          )
                   FROM quiz_statistic_counter qsc
                   WHERE qs.id = qsc.drag_and_drop_question_statistic_id
               ),
               'participantsRated', qs.participants_rated,
               'participantsUnrated', qs.participants_unrated,
               'ratedCorrectCounter', qs.rated_correct_counter,
               'unratedCorrectCounter', qs.un_rated_correct_counter
           )
    FROM quiz_statistic qs
    WHERE qs.id = q.quiz_question_statistic_id AND q.discriminator = 'DD'
)
WHERE q.discriminator = 'DD'; -- Only update drag-and-drop questions.


-- Update exercise table for quiz point statistics.
UPDATE exercise q
SET q.statistics = (
    -- Select JSON object containing point counters and participant data.
    SELECT JSON_OBJECT(
               'pointCounters', (
            -- Select JSON array of point counters with detailed points.
            SELECT JSON_ARRAYAGG(
                       JSON_OBJECT(
                           'id', qsc.id,
                           'points', qsc.points,
                           'ratedCounter', qsc.rated_counter,
                           'unratedCounter', qsc.un_rated_counter
                       )
                   )
            FROM quiz_statistic_counter qsc
            WHERE qsc.quiz_point_statistic_id = qs.id
        ),
               'participantsRated', qs.participants_rated,
               'participantsUnrated', qs.participants_unrated
           )
    FROM quiz_statistic qs
    WHERE qs.id = q.quiz_point_statistic_id
      AND qs.discriminator = 'QP'
)
WHERE EXISTS (
    -- Ensure corresponding quiz statistics exist for the exercise.
    SELECT *
    FROM quiz_statistic qs
    WHERE qs.id = q.quiz_point_statistic_id
      AND qs.discriminator = 'QP'
);



