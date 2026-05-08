/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}"
  ],
  theme: {
    extend: {
      colors: {
        background: "#ffffff",
        foreground: "#171717",
        primary: {
          DEFAULT: "#171717",
          foreground: "#ffffff"
        },
        ship: "#ff5b4f",
        preview: "#de1d8d",
        develop: "#0a72ef",
        geist: {
          gray: {
            50: "#fafafa",
            100: "#ebebeb",
            400: "#808080",
            500: "#666666",
            600: "#4d4d4d",
            900: "#171717"
          },
          blue: {
            focus: "hsla(212, 100%, 48%, 1)",
            ring: "rgba(147, 197, 253, 0.5)",
            badge: "#ebf5ff",
            badgeText: "#0068d6"
          }
        }
      },
      fontFamily: {
        sans: ["Geist", "Inter", "sans-serif"],
        mono: ["Geist Mono", "ui-monospace", "SFMono-Regular", "Menlo", "monospace"]
      },
      boxShadow: {
        "vercel-border": "0px 0px 0px 1px rgba(0, 0, 0, 0.08)",
        "vercel-elevation": "0px 0px 0px 1px rgba(0, 0, 0, 0.08), 0px 2px 2px rgba(0, 0, 0, 0.04)",
        "vercel-card": "0px 0px 0px 1px rgba(0,0,0,0.08), 0px 2px 2px rgba(0,0,0,0.04), 0px 8px 8px -8px rgba(0,0,0,0.04), 0px 0px 0px 1px #fafafa"
      },
      letterSpacing: {
        "vercel-display": "-0.06em",
        "vercel-heading": "-0.04em"
      },
      borderRadius: {
        "vercel-sm": "4px",
        "vercel-md": "6px",
        "vercel-lg": "8px",
        "vercel-xl": "12px"
      }
    }
  },
  plugins: []
};
