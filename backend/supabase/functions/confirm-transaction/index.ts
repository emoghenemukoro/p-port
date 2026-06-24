import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
    Deno.env.get("PROJECT_URL")!,
    Deno.env.get("SERVICE_ROLE_KEY")!
);

serve(async (req) => {
  try {
    const { transaction_id, role } = await req.json();
    if (!transaction_id || !role) throw new Error("Missing fields");

    const updateField =
        role === "customer"
            ? { confirmed_customer: true }
            : { confirmed_merchant: true };

    const { data, error } = await supabase
        .from("transactions")
        .update(updateField)
        .eq("id", transaction_id)
        .select("*, request:requests(type, amount)")
        .single();

    if (error) throw error;

    if (data.confirmed_customer && data.confirmed_merchant) {
      const commission = Math.round(data.markup * 0.2 * 100) / 100;

      // Get current wallet balance
      const { data: merchantProfile } = await supabase
          .from("profiles")
          .select("balance")
          .eq("id", data.merchant_id)
          .single();

      if (!merchantProfile) throw new Error("Merchant profile not found");

      let newBalance = merchantProfile.balance;

      // If this is a withdrawal, the merchant receives the total via POS → credit wallet
      if (data.request?.type === "withdraw") {
        newBalance += data.total_amount;
      }
      // For deposits, the merchant already collected cash, no wallet credit.

      // Deduct commission
      newBalance = Math.max(0, newBalance - commission);

      await supabase
          .from("profiles")
          .update({ balance: newBalance })
          .eq("id", data.merchant_id);

      await supabase
          .from("transactions")
          .update({
            status: "completed",
            receipt: {
              timestamp: new Date().toISOString(),
              total: data.total_amount,
              base: data.base_amount,
              markup: data.markup,
              commission: commission
            }
          })
          .eq("id", transaction_id);
    }

    const { data: updatedTx, error: fetchError } = await supabase
        .from("transactions")
        .select("*, request:requests(type, amount)")
        .eq("id", transaction_id)
        .single();

    if (fetchError) throw fetchError;

    const { data: newProfile } = await supabase
        .from("profiles")
        .select("balance")
        .eq("id", updatedTx.merchant_id)
        .single();

    return new Response(JSON.stringify({
      ...updatedTx,
      merchant_balance: newProfile?.balance ?? 0
    }), { status: 200 });

  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), { status: 400 });
  }
});