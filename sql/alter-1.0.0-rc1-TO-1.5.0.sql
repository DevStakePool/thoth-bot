ALTER TABLE IF EXISTS public.assets
    ADD COLUMN asset_display_name character varying(256);

ALTER TABLE IF EXISTS public.users
    RENAME stake_addr TO addr;