import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Relative base works for both GitHub Pages and the Android (Capacitor) app.
export default defineConfig({
  plugins: [react()],
  base: "./",
});
