--
-- (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
--
-- This file is part of the Kitodo project.
--
-- It is licensed under GNU General Public License version 3 or later.
--

-- This SQL script renames all tables to lowercase.
-- This is required for MariaDB on macOS or Windows because these
-- operating systems use case insensitive filesystems.

-- Table renames on such filesystems must use an intermediate table name
-- because a direct `RENAME TABLE A TO a;` does not work.

-- First, create a procedure to handle the renames
DELIMITER //

CREATE PROCEDURE rename_tables_to_lowercase()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE table_name_var VARCHAR(255);

    -- Cursor for all tables with uppercase characters
    DECLARE table_cursor CURSOR FOR
        SELECT TABLE_NAME
        FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_TYPE = 'BASE TABLE'
        AND TABLE_NAME IN ('client_x_listColumn', 'ldapGroup', 'ldapServer', 'listColumn', 'workflowCondition')
        AND BINARY TABLE_NAME != LOWER(TABLE_NAME);

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN table_cursor;

    rename_loop: LOOP
        FETCH table_cursor INTO table_name_var;
        IF done THEN
            LEAVE rename_loop;
        END IF;

        -- Execute renames using dynamic SQL
        SET @temp_name = CONCAT(LOWER(table_name_var), '_temp');
        SET @rename1 = CONCAT('RENAME TABLE `', table_name_var, '` TO `', @temp_name, '`');
        SET @rename2 = CONCAT('RENAME TABLE `', @temp_name, '` TO `', LOWER(table_name_var), '`');

        PREPARE stmt1 FROM @rename1;
        EXECUTE stmt1;
        DEALLOCATE PREPARE stmt1;

        PREPARE stmt2 FROM @rename2;
        EXECUTE stmt2;
        DEALLOCATE PREPARE stmt2;

    END LOOP;

    CLOSE table_cursor;
END //

DELIMITER ;

-- Execute the procedure
CALL rename_tables_to_lowercase();

-- Clean up
DROP PROCEDURE rename_tables_to_lowercase;
