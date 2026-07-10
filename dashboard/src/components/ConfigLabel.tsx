"use client";

import { useState } from "react";
import { CONFIG_DESCRIPTIONS } from "@/lib/configDescriptions";

/**
 * Nome de uma config + tooltip (balão) explicando o que ela faz, no hover.
 * O balão é position:fixed pra não ser cortado pelo overflow das seções.
 */
export default function ConfigLabel({
  name,
  className = "",
}: {
  name: string;
  className?: string;
}) {
  const desc = CONFIG_DESCRIPTIONS[name];
  const [pos, setPos] = useState<{ x: number; y: number } | null>(null);

  function show(e: React.MouseEvent<HTMLElement>) {
    if (!desc) return;
    const r = e.currentTarget.getBoundingClientRect();
    setPos({ x: r.left, y: r.top });
  }

  return (
    <span
      className={`inline-flex min-w-0 items-center gap-1.5 ${className}`}
      onMouseEnter={show}
      onMouseLeave={() => setPos(null)}
    >
      <span className="truncate text-xs font-medium text-text-primary">{name}</span>
      {desc && (
        <span className="grid h-3.5 w-3.5 shrink-0 cursor-help place-items-center rounded-full border border-border-light text-[9px] font-bold leading-none text-text-muted">
          ?
        </span>
      )}
      {desc && pos && (
        <span
          style={{
            position: "fixed",
            left: Math.max(8, Math.min(pos.x, (typeof window !== "undefined" ? window.innerWidth : 1200) - 296)),
            top: pos.y - 8,
            transform: "translateY(-100%)",
          }}
          className="pointer-events-none z-[100] block w-72 max-w-[85vw] rounded-lg border border-border-light bg-bg-card px-3 py-2 text-[11px] leading-snug text-text-secondary shadow-xl"
        >
          <span className="mb-1 block font-mono text-[10px] font-semibold text-accent-green">
            {name}
          </span>
          {desc}
        </span>
      )}
    </span>
  );
}
