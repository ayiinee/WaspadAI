import os
import json
import psycopg

case_id = "5a9207b9-cb65-4289-8a71-284566f4394a"
with psycopg.connect(os.environ["DATABASE_URL"]) as c:
    with c.cursor() as q:
        q.execute("select set_config('request.jwt.claims', %s, true)", (json.dumps({"sub": "e2c6eec0-e7ab-4d73-a711-aa4633708bfb", "role": "authenticated"}),))
        q.execute("""
            select c.id, c.user_id, c.input_type, c.community_state,
                   c.deleted_at, c.retention_expires_at,
                   p.id, p.owner_id, p.status, p.withdrawn_at,
                   p.publication_consent_id,
                   r.case_id
              from public.verification_cases c
              left join public.community_posts p on p.case_id = c.id
              left join public.verification_results r on r.case_id = c.id
             where c.id = %s
        """, (case_id,))
        print(q.fetchone())
