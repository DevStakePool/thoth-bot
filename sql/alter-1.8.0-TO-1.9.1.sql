-- Create new Retiring Pools table
CREATE TABLE public.retiring_pools (
    id bigint NOT NULL,
    chat_id bigint NOT NULL,
    pool_id character varying(128) NOT NULL,
    remaining_notifications integer NOT NULL
);

CREATE SEQUENCE public.retiring_pools_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.retiring_pools_id_seq OWNED BY public.retiring_pools.id;
ALTER TABLE ONLY public.retiring_pools ALTER COLUMN id SET DEFAULT nextval('public.retiring_pools_id_seq'::regclass);

ALTER TABLE ONLY public.retiring_pools
    ADD CONSTRAINT retiring_pools_pkey PRIMARY KEY (id);

