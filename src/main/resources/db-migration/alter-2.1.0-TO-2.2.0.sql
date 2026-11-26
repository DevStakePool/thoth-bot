-- Last block minted by a pool for which the admin has been notified
CREATE TABLE IF NOT EXISTS public.pool_blocks (
    pool_id character varying(128) PRIMARY KEY,
    last_block_height bigint NOT NULL
);
