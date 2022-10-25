ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS last_epoch_number integer DEFAULT 0 NOT NULL
