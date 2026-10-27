import type { NextConfig } from "next";

// Sem rewrites: /api/* é servido por src/app/api/[...path]/route.ts, que faz o proxy ao backend
// com timeout, logs descritivos no terminal e erros tratados (JSON amigável, nunca 500 cru).
const nextConfig: NextConfig = {};

export default nextConfig;
