-- Keep stored conversation titles aligned with the public PATCH contract.
-- Older rows may contain result headlines longer than the new 80-char limit.

update public.verification_conversations
   set title = left(regexp_replace(btrim(title), '\s+', ' ', 'g'), 80)
 where char_length(title) > 80
    or title <> regexp_replace(btrim(title), '\s+', ' ', 'g');

alter table public.verification_conversations
    drop constraint if exists verification_conversations_title_check;

alter table public.verification_conversations
    add constraint verification_conversations_title_check
    check (char_length(btrim(title)) between 1 and 80);
