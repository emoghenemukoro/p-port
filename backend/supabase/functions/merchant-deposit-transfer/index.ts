import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
    Deno.env.get("PROJECT_URL")!,
    Deno.env.get("SERVICE_ROLE_KEY")!
);

serve(async (req) => {
    try {
        const { transaction_id, customer_account_details } = await req.json();
        if (!transaction_id) throw new Error("Missing transaction_id");

        // Get the transaction
        const { data: tx, error: txError } = await supabase
            .from("transactions")
            .select("id, merchant_id, base_amount, status, confirmed_merchant")
            .eq("id", transaction_id)
            .single();

        if (txError || !tx) throw new Error("Transaction not found");
        if (tx.confirmed_merchant) throw new Error("Transfer already done");

        // Only allow if transaction is pending (or confirmed_by_customer)
        if (tx.status !== "pending" && tx.status !== "confirmed_by_customer") {
            throw new Error("Transaction cannot be transferred in current status");
        }

        // Get merchant's wallet
        const { data: profile } = await supabase
            .from("profiles")
            .select("balance")
            .eq("id", tx.merchant_id)
            .single();

        if (!profile) throw new Error("Merchant profile not found");
        if (profile.balance < tx.base_amount) throw new Error("Insufficient wallet balance");

        const newBalance = profile.balance - tx.base_amount;

        // Update wallet
        await supabase
            .from("profiles")
            .update({ balance: newBalance })
            .eq("id", tx.merchant_id);

        // Mark merchant confirmation and save customer account details
        await supabase
            .from("transactions")
            .update({
                confirmed_merchant: true,
                receipt: {
                    ...tx.receipt,
                    deposit_transfer_details: customer_account_details || {}
                }
            })
            .eq("id", transaction_id);

        return new Response(JSON.stringify({
            success: true,
            new_balance: newBalance
        }), { status: 200 });

    } catch (err) {
        return new Response(JSON.stringify({ error: err.message }), { status: 400 });
    }
});