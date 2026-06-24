import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
    Deno.env.get("PROJECT_URL")!,
    Deno.env.get("SERVICE_ROLE_KEY")!
);

serve(async (req) => {
  try {
    const { user_id, lat, lng } = await req.json();

    if (!user_id || !lat || !lng) {
      throw new Error("Missing fields");
    }

    const { error } = await supabase
        .from("profiles")
        .update({
          location: `POINT(${lng} ${lat})`,
          last_seen: new Date().toISOString()
        })
        .eq("id", user_id);

    if (error) throw error;

    return new Response(JSON.stringify({ success: true }));

  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), { status: 400 });
  }
});