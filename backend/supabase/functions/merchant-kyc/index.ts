import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
    Deno.env.get("PROJECT_URL")!,
    Deno.env.get("SERVICE_ROLE_KEY")!
);

const FW_SECRET_KEY = Deno.env.get("FLUTTERWAVE_SECRET_KEY")!;

serve(async (req) => {
    try {
        const { user_id, full_name, bvn, nin, bank_name, account_number } = await req.json();
        if (!user_id || !full_name || !bvn || !nin || !bank_name || !account_number) {
            throw new Error("Missing required fields");
        }

        // Step 1: Verify BVN with Flutterwave Identity API
        const bvnResponse = await fetch("https://api.flutterwave.com/v3/kyc/bvns", {
            method: "POST",
            headers: {
                Authorization: `Bearer ${FW_SECRET_KEY}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ bvn })
        });

        const bvnData = await bvnResponse.json();

        // Save KYC details regardless, then verify
        await supabase
            .from("profiles")
            .update({
                bvn: bvn,
                nin: nin,
                full_name: full_name,
                bank_details: {
                    bank_name: bank_name,
                    account_number: account_number
                },
                kyc_status: "in_progress"
            })
            .eq("id", user_id);

        // Step 2: Create Flutterwave sub-account
        const subaccountResponse = await fetch("https://api.flutterwave.com/v3/subaccounts", {
            method: "POST",
            headers: {
                Authorization: `Bearer ${FW_SECRET_KEY}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                account_bank: bank_name,
                account_number: account_number,
                business_name: full_name,
                business_email: `agent_${user_id.substring(0, 8)}@pport.com`,
                business_contact: "09000000000",
                business_mobile: "09000000000",
                country: "NG",
                split_type: "percentage",
                split_value: 0.2 // P-Port takes 20%
            })
        });

        const subaccountData = await subaccountResponse.json();

        if (subaccountData.status === "success") {
            await supabase
                .from("profiles")
                .update({
                    flutterwave_subaccount_id: subaccountData.data.subaccount_id,
                    kyc_status: "approved",
                    is_kyc_verified: true
                })
                .eq("id", user_id);
        } else {
            await supabase
                .from("profiles")
                .update({ kyc_status: "rejected" })
                .eq("id", user_id);
            throw new Error(subaccountData.message || "Sub-account creation failed");
        }

        return new Response(JSON.stringify({
            success: true,
            subaccount_id: subaccountData.data.subaccount_id
        }), { status: 200 });

    } catch (err) {
        return new Response(JSON.stringify({ error: err.message }), { status: 400 });
    }
});