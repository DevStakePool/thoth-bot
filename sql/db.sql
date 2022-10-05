--
-- PostgreSQL database dump
--

-- Dumped from database version 12.12 (Ubuntu 12.12-0ubuntu0.20.04.1)
-- Dumped by pg_dump version 12.12 (Ubuntu 12.12-0ubuntu0.20.04.1)

-- Started on 2022-10-05 21:29:11 CEST

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- TOC entry 2984 (class 1262 OID 16384)
-- Name: thoth_dev; Type: DATABASE; Schema: -; Owner: postgres
--

CREATE DATABASE thoth_dev WITH TEMPLATE = template0 ENCODING = 'UTF8' LC_COLLATE = 'en_US.UTF-8' LC_CTYPE = 'en_US.UTF-8';


ALTER DATABASE thoth_dev OWNER TO postgres;

\connect thoth_dev

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 203 (class 1259 OID 16387)
-- Name: users; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    chat_id bigint NOT NULL,
    stake_addr character varying(128) NOT NULL,
    last_block_height integer NOT NULL
);


ALTER TABLE public.users OWNER TO postgres;

--
-- TOC entry 204 (class 1259 OID 16448)
-- Name: assets_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.assets_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.assets_id_seq OWNER TO postgres;

--
-- TOC entry 2985 (class 0 OID 0)
-- Dependencies: 204
-- Name: assets_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.assets_id_seq OWNED BY public.users.id;


--
-- TOC entry 205 (class 1259 OID 16450)
-- Name: assets; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.assets (
    id bigint DEFAULT nextval('public.assets_id_seq'::regclass) NOT NULL,
    policy_id character varying(256) NOT NULL,
    asset_name character varying(512) NOT NULL,
    decimals integer NOT NULL
);


ALTER TABLE public.assets OWNER TO postgres;

--
-- TOC entry 202 (class 1259 OID 16385)
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.users_id_seq OWNER TO postgres;

--
-- TOC entry 2986 (class 0 OID 0)
-- Dependencies: 202
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- TOC entry 2845 (class 2604 OID 16390)
-- Name: users id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- TOC entry 2851 (class 2606 OID 16458)
-- Name: assets assets_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.assets
    ADD CONSTRAINT assets_pkey PRIMARY KEY (id);


--
-- TOC entry 2849 (class 2606 OID 16392)
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- TOC entry 2847 (class 1259 OID 16461)
-- Name: chat_id_stake_addr_index; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX chat_id_stake_addr_index ON public.users USING btree (chat_id, stake_addr);


--
-- TOC entry 2852 (class 1259 OID 16463)
-- Name: policy_id_asset_name_index; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX policy_id_asset_name_index ON public.assets USING btree (policy_id, asset_name);


-- Completed on 2022-10-05 21:29:11 CEST

--
-- PostgreSQL database dump complete
--

