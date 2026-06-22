/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        // TheragenX brand palette
        navy:  '#0C1A36',
        brand: '#0077B6',
        teal:  '#00C2E0',
        // Confidence level colours
        'conf-high':   '#16A34A', // green-600
        'conf-medium': '#D97706', // amber-600
        'conf-low':    '#DC2626', // red-600
      },
    },
  },
  plugins: [],
}
