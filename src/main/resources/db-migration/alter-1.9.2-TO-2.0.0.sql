ALTER TABLE IF EXISTS public.users
    ADD COLUMN last_gov_action_block_time BIGINT DEFAULT (extract(epoch from NOW())) NOT NULL