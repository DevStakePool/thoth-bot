DELETE from public.users;

-- Staking with DEV, 0 THOTH NFTs
INSERT INTO public.users(
	chat_id, stake_addr, last_block_height, last_epoch_number)
	VALUES (1683539744, 'stake1u9sgd4en77xucce36tntjyvepsn0r0m6gu0cx6nsqc3taggmeee4h', 99999999, 99999);


-- NON-Staking with DEV, 0 THOTH NFTs
INSERT INTO public.users(
	chat_id, stake_addr, last_block_height, last_epoch_number)
	VALUES (1683539744, 'stake1u8vs5rgfqxx63qy3xsnk2v546wffl3f7c8qvww9catxtnggfe6xxd', 99999999, 99999);

