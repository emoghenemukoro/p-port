import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("PROJECT_URL")!,
  Deno.env.get("SERVICE_ROLE_KEY")!
);

const FW_SECRET_KEY = Deno.env.get("FLUTTERWAVE_SECRET_KEY");
const PPORT_ACCOUNT = Deno.env.get("PPORT_FLUTTERWAVE_ACCOUNT_ID");

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
      .select("id, customer_id, amount, type")
      .eq("id", offer.request_id)
      .single();
    if (!request) throw new Error("Request not found");

    const { data: success } = await supabase.rpc("accept_offer_atomic", {
      offer_id_input: offer_id
    });
    if (!success) return new Response(JSON.stringify({ error: "Offer already accepted" }), { status: 409 });

    const markup = (request.amount * offer.markup_percent) / 100;
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

    // If it's a withdrawal, create a Flutterwave payment link
    if (request.type === "withdraw" && FW_SECRET_KEY && PPORT_ACCOUNT) {
      const { data: customer } = await supabase
        .from("profiles")
        .select("email")
        .eq("id", customer_id)
        .single();

      const { data: merchant } = await supabase
        .from("profiles")
        .select("flutterwave_subaccount_id")
        .eq("id", offer.merchant_id)
        .single();

      const subaccounts = [];
      if (merchant?.flutterwave_subaccount_id) {
        subaccounts.push({ id: merchant.flutterwave_subaccount_id, transaction_charge_type: "flat_subaccount", transaction_charge: 0 });
      }
      subaccounts.push({ id: PPORT_ACCOUNT, transaction_charge_type: "flat_subaccount", transaction_charge: 0 });

      const fwResponse = await fetch("https://api.flutterwave.com/v3/payments", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${FW_SECRET_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          tx_ref: `pport_${request.id}_${Date.now()}`,
          amount: total,
          currency: "NGN",
          redirect_url: "https://example.com/redirect",
          customer: { email: customer?.email || "customer@example.com" },
          subaccounts,
          meta: { request_id: request.id, offer_id },
          customizations: {
            title: "P-Port Withdrawal",
            description: `Pay ₦${total} to withdraw ₦${request.amount}`
          }
        })
      });

      const fwData = await fwResponse.json();
      if (!fwResponse.ok) throw new Error(fwData.message || "Flutterwave error");

      return new Response(JSON.stringify({
        success: true,
        payment_link: fwData.data.link,
        tx_ref: fwData.data.tx_ref,
        transaction: tx
      }), { status: 200 });
    }

    // For deposits or if Flutterwave not configured, just return the transaction
    return new Response(JSON.stringify({ success: true, transaction: tx }), { status: 200 });

  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), { status: 400 });
  }
});