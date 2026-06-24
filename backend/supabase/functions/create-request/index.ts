import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
    Deno.env.get("PROJECT_URL")!,
    Deno.env.get("SERVICE_ROLE_KEY")!
);

serve(async (req) => {
  try {
    const { user_id, lat, lng, amount, type } = await req.json();

    if (!user_id || !lat || !lng || !amount || !type) {
      throw new Error("Missing required fields");
    }

    // ✅ FIX: use 'open' (allowed by the table constraint)
    const { data: request, error } = await supabase
        .from("requests")
        .insert({
          customer_id: user_id,
          amount,
          type,
          location: `POINT(${lng} ${lat})`,
          status: "open",                // ← was "searching", now "open"
          expires_at: new Date(Date.now() + 2 * 60 * 1000)
        })
        .select()
        .single();

    if (error) throw error;

    // Optional: trigger matching (if you want)
    await fetch(`${Deno.env.get("PROJECT_URL")}/functions/v1/match-merchants`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${Deno.env.get("SERVICE_ROLE_KEY")}`
      },
      body: JSON.stringify({
        request_id: request.id,
        lat,
        lng,
        amount
      })
    });

    return new Response(JSON.stringify(request), { status: 200 });
  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), { status: 400 });
  }
});