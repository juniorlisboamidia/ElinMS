import { NextRequest, NextResponse } from "next/server";

const GAME_API_URL = process.env.GAME_API_URL || "http://elin-ms-game.internal:8585";
const BASE = process.env.COSMIC_DASHBOARD_URL || "http://localhost:3000";

// Unified teleport: if the player is ONLINE, warp them live; otherwise fall back
// to a DB map update (offline, applies on next login). Requires characterId.
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { characterId, mapId } = body;
    if (!characterId) return NextResponse.json({ error: "characterId is required" }, { status: 400 });
    if (mapId === undefined || mapId === null) return NextResponse.json({ error: "mapId is required" }, { status: 400 });

    // 1) Try the live warp (works only if the player is online)
    const live = await fetch(`${GAME_API_URL}/warp`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ characterId, mapId }),
    });
    if (live.ok) {
      const d = await live.json();
      return NextResponse.json({ ...d, applied: "live" });
    }

    // 2) Offline fallback: DB map update (applies on next login)
    const db = await fetch(`${BASE}/api/characters/${characterId}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        "x-gm-secret": process.env.GM_API_SECRET || "",
      },
      body: JSON.stringify({ map: mapId }),
    });
    const d = await db.json();
    if (!db.ok) return NextResponse.json(d, { status: db.status });
    return NextResponse.json({ ...d, applied: "offline" });
  } catch (err: any) {
    return NextResponse.json(
      { error: "Failed to teleport.", details: err.message },
      { status: 500 },
    );
  }
}
