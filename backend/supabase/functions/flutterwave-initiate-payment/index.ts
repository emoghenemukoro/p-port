import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
    Deno.env.get("PROJECT_URL")!,
    Deno.env.get("SERVICE_ROLE_KEY")!
);

const FW_SECRET_KEY = Deno.env.get("FLUTTERWAVE_SECRET_KEY")!;

serve(async (req) => {
    try {
        const { request_id, offer_id, redirect_url } = await req.json();
        if (!request_id || !offer_id) throw new Error("Missing request_id or offer_id");

        // Fetch offer with request details
        const { data: offer, error: offerError } = await supabase
            .from("offers")
            .select("id, markup_percent, merchant_id, request:requests(id, amount, type, customer_id)")
            .eq("id", offer_id)
            .single();

        if (offerError || !offer) throw new Error("Offer not found");

        // Fetch customer email
        const { data: customer } = await supabase
            .from("profiles")
            .select("email")
            .eq("id", offer.request.customer_id)
            .single();

        const customerEmail = customer?.email || "customer@example.com";

        const amount = offer.request.amount;
        const markupPercent = offer.markup_percent;
        const totalAmount = Math.round(amount * (1 + markupPercent / 100) * 100) / 100;

        const txRef = `pport_${request_id}_${Date.now()}`;

        // Create payment link via Flutterwave
        const fwResponse = await fetch("https://api.flutterwave.com/v3/payments", {
            method: "POST",
            headers: {
                Authorization: `Bearer ${FW_SECRET_KEY}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                tx_ref: txRef,
                amount: totalAmount,
                currency: "NGN",
                redirect_url: redirect_url || "pport://payment-complete",
                customer: {
                    email: customerEmail
                },
                meta: {
                    request_id: request_id,
                    offer_id: offer_id
                },
                customizations: {
                    title: "P-Port Withdrawal",
                    description: `Pay ₦${totalAmount} to withdraw ₦${amount}`,
                    logo: "https://your-logo-url.com/logo.png" // Replace with your logo URL
                }
            })
        });

        const fwData = await fwResponse.json();
        if (!fwResponse.ok) throw new Error(fwData.message || "Flutterwave error");

        return new Response(JSON.stringify({
            ok: true,
            payment_link: fwData.data.link,
            tx_ref: txRef,
            customer_pays: totalAmount
        }), { status: 200 });

    } catch (err) {
        return new Response(JSON.stringify({ error: err.message }), { status: 400 });
    }
});