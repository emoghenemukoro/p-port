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
      .select()
      .single();

    if (error) throw error;

    if (data.confirmed_customer && data.confirmed_merchant) {
      // Both confirmed → complete and generate receipt
      await supabase
        .from("transactions")
        .update({
          status: "completed",
          receipt: {
            timestamp: new Date().toISOString(),
            total: data.total_amount,
            base: data.base_amount,
            markup: data.markup
          }
        })
        .eq("id", transaction_id);
    }

    // Fetch the final state
    const { data: updatedTx, error: fetchError } = await supabase
      .from("transactions")
      .select("*")
      .eq("id", transaction_id)
      .single();

    if (fetchError) throw fetchError;

    return new Response(JSON.stringify(updatedTx), { status: 200 });

  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), { status: 400 });
  }
});