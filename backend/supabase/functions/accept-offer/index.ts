import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
    Deno.env.get("PROJECT_URL")!,
    Deno.env.get("SERVICE_ROLE_KEY")!
);

serve(async (req) => {
    try {
        const { offer_id, customer_id } = await req.json();
        if (!offer_id || !customer_id) throw new Error("Missing fields");

        const { data: offer } = await supabase
            .from("offers")
            .select("*")
            .eq("id", offer_id)
            .single();
        if (!offer) throw new Error("Offer not found");

        const { data: request } = await supabase
            .from("requests")
            .select("id, amount, type")
            .eq("id", offer.request_id)
            .single();
        if (!request) throw new Error("Request not found");

        // Atomic accept
        const { data: success } = await supabase.rpc("accept_offer_atomic", {
            offer_id_input: offer_id
        });
        if (!success) return new Response(JSON.stringify({ error: "Offer already accepted" }), { status: 409 });

        const markup = Math.round(request.amount * offer.markup_percent) / 100;
        const total = request.amount + markup;

        const { data: tx, error: txError } = await supabase
            .from("transactions")
            .insert({
                request_id: request.id,
                offer_id,
                customer_id,
                merchant_id: offer.merchant_id,
                base_amount: request.amount,
                markup,
                total_amount: total,
                status: "pending"
            })
            .select()
            .single();

        if (txError) throw txError;

        // For withdrawals, generate Flutterwave payment link
        if (request.type === "withdraw") {
            const paymentResponse = await fetch(
                `${Deno.env.get("PROJECT_URL")}/functions/v1/flutterwave-initiate-payment`,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        Authorization: `Bearer ${Deno.env.get("SERVICE_ROLE_KEY")}`
                    },
                    body: JSON.stringify({
                        request_id: request.id,
                        offer_id: offer_id,
                        redirect_url: "pport://payment-complete"
                    })
                }
            );

            const paymentData = await paymentResponse.json();

            if (paymentData.ok) {
                return new Response(JSON.stringify({
                    success: true,
                    payment_link: paymentData.payment_link,
                    tx_ref: paymentData.tx_ref,
                    transaction: tx
                }), { status: 200 });
            }
        }

        // For deposits, just return the transaction
        return new Response(JSON.stringify({ success: true, transaction: tx }), { status: 200 });

    } catch (err) {
        return new Response(JSON.stringify({ error: err.message }), { status: 400 });
    }
});