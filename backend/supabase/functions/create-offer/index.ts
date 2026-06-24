import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
    Deno.env.get("PROJECT_URL")!,
    Deno.env.get("SERVICE_ROLE_KEY")!
);

serve(async (req) => {
  try {
    const { request_id, merchant_id, markup } = await req.json();

    if (!request_id || !merchant_id || markup == null) {
      throw new Error("Missing fields");
    }

    // Check request still valid — allow 'open' and 'negotiating'
    const { data: request } = await supabase
        .from("requests")
        .select("status")
        .eq("id", request_id)
        .single();

    if (!request || (request.status !== "open" && request.status !== "negotiating")) {
      throw new Error("Request no longer active");
    }

    // Limit offers to 3 per request
    const { count } = await supabase
        .from("offers")
        .select("*", { count: "exact", head: true })
        .eq("request_id", request_id);

    if (count >= 3) {
      throw new Error("Offer limit reached");
    }

    const { data, error } = await supabase
        .from("offers")
        .insert({
          request_id,
          merchant_id,
          markup
        });

    if (error) throw error;

    return new Response(JSON.stringify(data), { status: 200 });

  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), { status: 400 });
  }
});