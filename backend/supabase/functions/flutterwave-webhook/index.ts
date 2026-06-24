import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
    Deno.env.get("PROJECT_URL")!,
    Deno.env.get("SERVICE_ROLE_KEY")!
);

const FW_SECRET_KEY = Deno.env.get("FLUTTERWAVE_SECRET_KEY")!;
const FW_WEBHOOK_HASH = Deno.env.get("FLUTTERWAVE_WEBHOOK_HASH")!;

serve(async (req) => {
    try {
        const signature = req.headers.get("verif-hash") || "";

        // Verify webhook
        if (FW_WEBHOOK_HASH && signature !== FW_WEBHOOK_HASH) {
            return new Response("Unauthorized", { status: 401 });
        }

        const payload = await req.json();

        if (payload.event === "charge.completed" && payload.data?.status === "successful") {
            const meta = payload.data.meta;
            const requestId = meta?.request_id;

            if (requestId) {
                const { data: transactions } = await supabase
                    .from("transactions")
                    .select("id")
                    .eq("request_id", requestId)
                    .eq("status", "pending")
                    .limit(1);

                if (transactions && transactions.length > 0) {
                    await supabase
                        .from("transactions")
                        .update({ confirmed_customer: true })
                        .eq("id", transactions[0].id);
                }
            }
        }

        return new Response(JSON.stringify({ status: "ok" }), { status: 200 });

    } catch (err) {
        console.error("Webhook error:", err);
        return new Response(JSON.stringify({ error: err.message }), { status: 400 });
    }
});