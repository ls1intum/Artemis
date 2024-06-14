-- Drop foreign key constraints referencing the answer_option table
ALTER TABLE quiz_statistic_counter DROP FOREIGN KEY FKg7hjug3wu6icklf6gbiqs4n18;
ALTER TABLE multiple_choice_submitted_answer_selected_options DROP FOREIGN KEY FK87gmes7g3ad3qf3wmx3lu0iq0;

-- Drop the answer_option table
DROP TABLE IF EXISTS answer_option;


-- Drop the drag_and_drop_mapping table
DROP TABLE IF EXISTS drag_and_drop_mapping;


-- Drop the drag_item table
DROP TABLE IF EXISTS drag_item;


-- Drop foreign key constraints referencing the drop_location table
ALTER TABLE quiz_statistic_counter DROP FOREIGN KEY FK2bses6ev8komaj0jw4gcyl8te;

-- Drop the drop_location table
DROP TABLE IF EXISTS drop_location;

-- Drop the drop_location_id column from the quiz_statistic_counter table
ALTER TABLE quiz_statistic_counter DROP COLUMN drop_location_id;


-- Drop the short_answer_mapping table
DROP TABLE IF EXISTS short_answer_mapping;


-- Drop foreign key constraints referencing the short_answer_spot table
ALTER TABLE quiz_statistic_counter DROP FOREIGN KEY FKoqgu1clyd02qbo86silw1uhmk;
ALTER TABLE short_answer_submitted_text DROP FOREIGN KEY FKpkb6e1yjqhma5tgvabb9smyv4;

-- Drop the short_answer_spot table
DROP TABLE IF EXISTS short_answer_spot;


-- Drop the short_answer_solution table
DROP TABLE IF EXISTS short_answer_solution;


-- Drop the short_answer_submitted_text table
DROP TABLE IF EXISTS short_answer_submitted_text;


-- Drop the quiz_statistic_counter table
DROP TABLE IF EXISTS quiz_statistic_counter;


-- Drop the quiz_point_statistic_id column
ALTER TABLE artemis.exercise DROP COLUMN quiz_point_statistic_id;
