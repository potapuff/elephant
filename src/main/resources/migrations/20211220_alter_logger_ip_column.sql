-- https://github.com/potapuff/elephant/issues/3
-- @author Fluffy777

alter table logger alter column ip type varchar(45) using ip::varchar(45);