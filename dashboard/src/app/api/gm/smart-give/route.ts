import { NextRequest, NextResponse } from "next/server";

const GAME_API_URL = process.env.GAME_API_URL || "http://elin-ms-game.internal:8585";
const BASE = process.env.COSMIC_DASHBOARD_URL || "http://localhost:3000";

// Unified give-item: if the player is ONLINE, add it live; otherwise fall back
// to a DB inventory insert (offline). Requires characterId.
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { characterId, itemId, quantity = 1 } = body;
    if (!characterId) return NextResponse.json({ error: "characterId is required" }, { status: 400 });
    if (!itemId) return NextResponse.json({ error: "itemId is required" }, { status: 400 });

    // 1) Try the live path (works only if the player is online)
    const live = await fetch(`${GAME_API_URL}/giveitem`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ characterId, itemId, quantity }),
    });
    if (live.ok) {
      const d = await live.json();
      return NextResponse.json({ ...d, applied: "live" });
    }

    // 2) Offline fallback: DB inventory insert (applies on next login)
    const db = await fetch(`${BASE}/api/characters/${characterId}/inventory`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-gm-secret": process.env.GM_API_SECRET || "",
      },
      body: JSON.stringify({ itemId, quantity }),
    });
    const d = await db.json();
    if (!db.ok) return NextResponse.json(d, { status: db.status });
    return NextResponse.json({ ...d, applied: "offline" });
  } catch (err: any) {
    return NextResponse.json(
      { error: "Failed to give item.", details: err.message },
      { status: 500 },
    );
  }
}
