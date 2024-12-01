-- Sakila Sample Database Data
-- Version 1.5
-- Reduced to required data

-- Copyright (c) 2006, 2024, Oracle and/or its affiliates.

-- Redistribution and use in source and binary forms, with or without
-- modification, are permitted provided that the following conditions are
-- met:

-- * Redistributions of source code must retain the above copyright notice,
--   this list of conditions and the following disclaimer.
-- * Redistributions in binary form must reproduce the above copyright
--   notice, this list of conditions and the following disclaimer in the
--   documentation and/or other materials provided with the distribution.
-- * Neither the name of Oracle nor the names of its contributors may be used
--   to endorse or promote products derived from this software without
--   specific prior written permission.

-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
-- IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
-- THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
-- PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
-- CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
-- EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
-- PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
-- PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
-- LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
-- NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
-- SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

SET NAMES utf8mb4;
SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='TRADITIONAL';
SET @old_autocommit=@@autocommit;

USE test;

INSERT INTO address VALUES
(1,'47 MySakila Drive',NULL,'Alberta',1,'','',/*!50705 0x0000000001010000003E0A325D63345CC0761FDB8D99D94840,*/'2014-09-25 22:30:27');

INSERT INTO city VALUES
(1,'Lethbridge',1,'2006-02-15 04:45:25');

INSERT INTO country VALUES
(1,'Canada','2006-02-15 04:44:00');

INSERT INTO customer VALUES
(1,1,'MARY','SMITH','MARY.SMITH@sakilacustomer.org',1,1,'2006-02-14 22:04:36','2006-02-15 04:57:20');

INSERT INTO film VALUES
(1,'ACADEMY DINOSAUR','A Epic Drama of a Feminist And a Mad Scientist who must Battle a Teacher in The Canadian Rockies',2006,1,NULL,6,'0.99',86,'20.99','PG','Deleted Scenes,Behind the Scenes','2006-02-15 05:03:42');

INSERT INTO inventory VALUES
(1,1,1,'2006-02-15 05:09:17');

INSERT INTO language VALUES
(1,'English','2006-02-15 05:02:19');

INSERT INTO staff VALUES
(1,'Mike','Hillyer',1,NULL,'Mike.Hillyer@sakilastaff.com',1,1,'Mike','8cb2237d0679ca88db6464eac60da96345513964','2006-02-15 03:57:16');

INSERT INTO store VALUES
(1,1,1,'2006-02-15 04:57:12');

COMMIT;

SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;
SET autocommit=@old_autocommit;
