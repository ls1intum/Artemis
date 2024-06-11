# UPDATE quiz_question q
# SET q.content = (
#     SELECT JSON_ARRAYAGG(
#                JSON_OBJECT(
#                    'id', ao.id,
#                    'text', ao.text,
#                    'hint', ao.hint,
#                    'explanation', ao.explanation,
#                    'is_correct', ao.is_correct,
#                    'invalid', ao.invalid
#                )
#            )
#     FROM answer_option ao
#     WHERE ao.question_id = q.id
# )
# WHERE q.discriminator = 'MC';


# UPDATE quiz_question q
# SET q.content = (
#     SELECT JSON_OBJECT(
#                'spots', (
#             SELECT JSON_ARRAYAGG(
#                        JSON_OBJECT(
#                            'id', sas.id,
#                            'invalid', sas.invalid,
#                            'width', sas.width,
#                            'spotNr', sas.spot_nr
#                        )
#                    )
#             FROM short_answer_spot sas
#             WHERE sas.question_id = q.id AND q.discriminator = 'SA'
#         ),
#                'solutions', (
#                    SELECT JSON_ARRAYAGG(
#                               JSON_OBJECT(
#                                   'id', saso.id,
#                                   'invalid', saso.invalid,
#                                   'text', saso.text
#                               )
#                           )
#                    FROM short_answer_solution saso
#                    WHERE saso.question_id = q.id AND q.discriminator = 'SA'
#                ),
#                'correctMappings', (
#                    SELECT JSON_ARRAYAGG(
#                               JSON_OBJECT(
#                                   'id', sam.id,
#                                   'spot', (
#                                       SELECT JSON_OBJECT(
#                                                  'id', sas.id,
#                                                  'invalid', sas.invalid,
#                                                  'width', sas.width,
#                                                  'spotNr', sas.spot_nr
#                                              )
#                                       FROM short_answer_spot sas
#                                       WHERE sas.id = sam.spot_id
#                                   ),
#                                   'invalid', sam.invalid,
#                                   'solution', (
#                                       SELECT JSON_OBJECT(
#                                                  'id', saso.id,
#                                                  'invalid', saso.invalid,
#                                                  'text', saso.text
#                                              )
#                                       FROM short_answer_solution saso
#                                       WHERE saso.id = sam.solution_id
#                                   ),
#                                   'shortAnswerSpotIndex', sam.short_answer_spot_index,
#                                   'shortAnswerSolutionIndex', sam.short_answer_solution_index
#                               )
#                           )
#                    FROM short_answer_mapping sam
#                    WHERE sam.question_id = q.id
#                )
#            )
# )
# WHERE q.discriminator = 'SA';


# UPDATE quiz_question q
# SET q.content = (
#     SELECT JSON_OBJECT(
#                'dropLocations', (
#             SELECT JSON_ARRAYAGG(
#                        JSON_OBJECT(
#                            'id', dl.id,
#                            'posX', dl.pos_x,
#                            'posY', dl.pos_y,
#                            'width', dl.width,
#                            'height', dl.height
#                        )
#                    )
#             FROM drop_location dl
#             WHERE dl.question_id = q.id AND q.discriminator = 'DD'
#         ),
#                'dragItems', (
#                    SELECT JSON_ARRAYAGG(
#                               JSON_OBJECT(
#                                   'id', di.id,
#                                   'invalid', di.invalid,
#                                   'text', di.text,
#                                   'pictureFilePath', di.picture_file_path
#                               )
#                           )
#                    FROM drag_item di
#                    WHERE di.question_id = q.id AND q.discriminator = 'DD'
#                ),
#                'correctMappings', (
#                    SELECT JSON_ARRAYAGG(
#                               JSON_OBJECT(
#                                   'id', ddm.id,
#                                   'dragItem', (
#                                       SELECT JSON_OBJECT(
#                                                  'id', di.id,
#                                                  'invalid', di.invalid,
#                                                  'text', di.text,
#                                                  'pictureFilePath', di.picture_file_path
#                                              )
#                                       FROM drag_item di
#                                       WHERE di.id = ddm.drag_item_id
#                                   ),
#                                   'invalid', ddm.invalid,
#                                   'dropLocation', (
#                                       SELECT JSON_OBJECT(
#                                                  'id', dl.id,
#                                                  'posX', dl.pos_x,
#                                                  'posY', dl.pos_y,
#                                                  'width', dl.width,
#                                                  'height', dl.height
#                                              )
#                                       FROM drop_location dl
#                                       WHERE dl.id = ddm.drop_location_id
#                                   ),
#                                   'dragItemIndex', ddm.drag_item_index,
#                                   'dropLocationIndex', ddm.drop_location_index
#                               )
#                           )
#                    FROM drag_and_drop_mapping ddm
#                    WHERE ddm.question_id = q.id
#                )
#            )
# )
# WHERE q.discriminator = 'DD';


# UPDATE quiz_question q
# SET q.statistics = (
#     SELECT JSON_OBJECT(
#                'id', qs.id,
#                'type', 'multiple-choice',
#                'answerCounters', (
#                    SELECT JSON_ARRAYAGG(
#                               JSON_OBJECT(
#                                   'id', qsc.id,
#                                   'ratedCounter', qsc.rated_counter,
#                                   'unratedCounter', qsc.un_rated_counter,
#                                   'answer', (
#                                       SELECT JSON_OBJECT(
#                                                  'id', ao.id,
#                                                  'text', ao.text,
#                                                  'hint', ao.hint,
#                                                  'explanation', ao.explanation,
#                                                  'is_correct', ao.is_correct,
#                                                  'invalid', ao.invalid
#                                              )
#                                       FROM answer_option ao
#                                       WHERE ao.id = qsc.answer_id
#                                   )
#                               )
#                           )
#                    FROM quiz_statistic_counter qsc
#                    WHERE qs.id = qsc.multiple_choice_question_statistic_id
#                ),
#                'participantsRated', qs.participants_rated,
#                'participantsUnrated', qs.participants_unrated,
#                'ratedCorrectCounter', qs.rated_correct_counter,
#                'unratedCorrectCounter', qs.un_rated_correct_counter
#            )
#     FROM quiz_statistic qs
#     WHERE qs.id = q.quiz_question_statistic_id AND q.discriminator = 'MC'
# )
# WHERE q.discriminator = 'MC';

#
# UPDATE quiz_question q
# SET q.statistics = (
#     SELECT JSON_OBJECT(
#                'id', qs.id,
#                'type', 'short-answer',
#                'shortAnswerSpotCounters', (
#                    SELECT JSON_ARRAYAGG(
#                               JSON_OBJECT(
#                                   'id', qsc.id,
#                                   'ratedCounter', qsc.rated_counter,
#                                   'unratedCounter', qsc.un_rated_counter,
#                                   'spot', (
#                                       SELECT JSON_OBJECT(
#                                                  'id', sas.id,
#                                                  'invalid', sas.invalid,
#                                                  'width', sas.width,
#                                                  'spotNr', sas.spot_nr
#                                              )
#                                       FROM short_answer_spot sas
#                                       WHERE sas.id = qsc.spot_id
#                                   )
#                               )
#                           )
#                    FROM quiz_statistic_counter qsc
#                    WHERE qs.id = qsc.short_answer_question_statistic_id
#                ),
#                'participantsRated', qs.participants_rated,
#                'participantsUnrated', qs.participants_unrated,
#                'ratedCorrectCounter', qs.rated_correct_counter,
#                'unratedCorrectCounter', qs.un_rated_correct_counter
#            )
#     FROM quiz_statistic qs
#     WHERE qs.id = q.quiz_question_statistic_id AND q.discriminator = 'SA'
# )
# WHERE q.discriminator = 'SA';
#
#
# UPDATE quiz_question q
# SET q.statistics = (
#     SELECT JSON_OBJECT(
#                'id', qs.id,
#                'type', 'drag-and-drop',
#                'dropLocationCounters', (
#                    SELECT JSON_ARRAYAGG(
#                               JSON_OBJECT(
#                                   'id', qsc.id,
#                                   'ratedCounter', qsc.rated_counter,
#                                   'unratedCounter', qsc.un_rated_counter,
#                                   'dropLocation', (
#                                       SELECT JSON_OBJECT(
#                                                  'id', dl.id,
#                                                  'posX', dl.pos_x,
#                                                  'posY', dl.pos_y,
#                                                  'width', dl.width,
#                                                  'height', dl.height
#                                              )
#                                       FROM drop_location dl
#                                       WHERE dl.id = qsc.drop_location_id
#                                   )
#                               )
#                           )
#                    FROM quiz_statistic_counter qsc
#                    WHERE qs.id = qsc.drag_and_drop_question_statistic_id
#                ),
#                'participantsRated', qs.participants_rated,
#                'participantsUnrated', qs.participants_unrated,
#                'ratedCorrectCounter', qs.rated_correct_counter,
#                'unratedCorrectCounter', qs.un_rated_correct_counter
#            )
#     FROM quiz_statistic qs
#     WHERE qs.id = q.quiz_question_statistic_id AND q.discriminator = 'DD'
# )
# WHERE q.discriminator = 'DD';
#
#
# UPDATE quiz_exercise q
# SET q.statistics = (
#     SELECT JSON_OBJECT(
#                'pointCounters', (
#             SELECT JSON_ARRAYAGG(
#                        JSON_OBJECT(
#                            'id', qsc.id,
#                            'points', qsc.points,
#                            'ratedCounter', qsc.rated_counter,
#                            'unratedCounter', qsc.un_rated_counter
#                        )
#                    )
#             FROM quiz_statistic_counter qsc
#             WHERE qsc.quiz_point_statistic_id = qs.id
#         ),
#                'participantsRated', qs.participants_rated,
#                'participantsUnrated', qs.participants_unrated
#            )
#     FROM quiz_statistic qs
#     WHERE qs.id = q.quiz_point_statistic_id
# )
# WHERE q.discriminator = 'QP';
#

UPDATE artemis.submitted_answer s
SET s.selection = (
    SELECT JSON_OBJECT(
           'selectedOptions',
           (SELECT JSON_ARRAYAGG(
                JSON_OBJECT(
                      'id', ao.id,
                      'text', ao.text,
                      'hint', ao.hint,
                      'explanation', ao.explanation,
                      'is_correct', ao.is_correct,
                      'invalid', ao.invalid
                  )
              )
               FROM answer_option ao
               WHERE ao.question_id = s.quiz_question_id)
           )
)
WHERE s.discriminator = 'MC';

#
#
# UPDATE artemis.submitted_answer s
# SET s.selection = (
#     SELECT JSON_OBJECT(
#                'submittedTexts', (
#             SELECT JSON_ARRAYAGG(
#                        JSON_OBJECT(
#                            'id', sast.id,
#                            'text', sast.text,
#                            'isCorrect', sast.is_correct,
#                            'spot', (
#                                SELECT JSON_OBJECT(
#                                           'id', sas.id,
#                                           'invalid', sas.invalid,
#                                           'width', sas.width,
#                                           'spotNr', sas.spot_nr
#                                       )
#                                FROM short_answer_spot sas
#                                WHERE sas.id = sast.spot_id
#                            )
#                        )
#                    )
#             FROM short_answer_submitted_text sast
#             WHERE sast.submitted_answer_id = s.id
#         )
#            )
# )
# WHERE s.discriminator = 'SA';
#
#
# UPDATE artemis.submitted_answer s
# SET s.selection = (
#     SELECT JSON_OBJECT(
#                'mappings', (
#             SELECT JSON_ARRAYAGG(
#                        JSON_OBJECT(
#                            'id', ddm.id,
#                            'dragItemIndex', ddm.drag_item_index,
#                            'dropLocationIndex', ddm.drop_location_index,
#                            'invalid', ddm.invalid,
#                            'dragItem', (
#                                SELECT JSON_OBJECT(
#                                           'id', di.id,
#                                           'invalid', di.invalid,
#                                           'text', di.text,
#                                           'pictureFilePath', di.picture_file_path
#                                       )
#                                FROM drag_item di
#                                WHERE di.id = ddm.drag_item_id
#                            ),
#                            'dropLocation', (
#                                SELECT JSON_OBJECT(
#                                           'id', dl.id,
#                                           'posX', dl.pos_x,
#                                           'posY', dl.pos_y,
#                                           'width', dl.width,
#                                           'height', dl.height
#                                       )
#                                FROM drop_location dl
#                                WHERE dl.id = ddm.drop_location_id
#                            )
#                        )
#                    )
#             FROM drag_and_drop_mapping ddm
#             WHERE ddm.submitted_answer_id = s.id
#         )
#            )
# )
# WHERE s.discriminator = 'DD';


