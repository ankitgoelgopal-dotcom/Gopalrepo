import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Base path matches the repo name so GitHub Pages serves assets correctly.
export default defineConfig({
  plugins: [react()],
  base: "/Gopalrepo/",
});
