import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("PROJECT_URL")!,
  Deno.env.get("SERVICE_ROLE_KEY")!
);

serve(async (req) => {
  try {
    const { request_id, lat, lng, amount } = await req.json();

    const { data: merchants, error } = await supabase.rpc("get_nearby_merchants", {
      user_lat: lat,
      user_lng: lng
    });

    if (error) throw error;

    const filtered = merchants
      .filter((m: any) => m.max_transaction >= amount)
      .slice(0, 3);

    return new Response(JSON.stringify(filtered), { status: 200 });

  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), { status: 400 });
  }
});

