-- Create new Pools Votes table
CREATE TABLE public.pool_votes (
    id bigint NOT NULL,
    gov_id character varying(128) NOT NULL,
    pool_id character varying(128) NOT NULL,
    block_time bigint NOT NULL
);

CREATE SEQUENCE public.pool_votes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.pool_votes_id_seq OWNED BY public.pool_votes.id;
ALTER TABLE ONLY public.pool_votes ALTER COLUMN id SET DEFAULT nextval('public.pool_votes_id_seq'::regclass);

ALTER TABLE ONLY public.pool_votes
    ADD CONSTRAINT pool_votes_pkey PRIMARY KEY (id);
