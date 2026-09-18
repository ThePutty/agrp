import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // samostatný build pro běh v Dockeru přes `node server.js`
  output: "standalone",
};

export default nextConfig;
