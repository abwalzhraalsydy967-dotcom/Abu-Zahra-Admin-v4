import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Produce a self-contained production build (.next/standalone) that bundles
  // only the needed node_modules — ideal for deploying to the server without
  // shipping the full node_modules tree.
  output: "standalone",
};

export default nextConfig;
