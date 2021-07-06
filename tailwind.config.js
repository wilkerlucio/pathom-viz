// const defaultTheme = require('tailwindcss/defaultTheme')

const PROD_PATHS = [
  "./shells/embed/js/main.js",
  "./shells/electron/js/renderer/main.js",
  "./shells/electron/js/renderer/shared.js",
  "./shells/electron/js/renderer/worker.js"
];

const DEV_PATHS = [
  "./shells/embed/js/cljs-runtime/*.js",
  "./shells/electron/js/renderer/cljs-runtime/*.js"
]

module.exports = {
  mode: 'jit',
  important: true,
  purge: {
    // in prod look at shadow-cljs output file in dev look at runtime, which will change files that are actually compiled; postcss watch should be a whole lot faster
    content: process.env.NODE_ENV == 'production' ? PROD_PATHS : DEV_PATHS
  },
  // darkMode: "class", // or 'media' or 'class'
  // theme: {
  //   extend: {
  //     fontFamily: {
  //       sans: ["Inter var", ...defaultTheme.fontFamily.sans],
  //     },
  //   },
  // },
  plugins: [
    require('@tailwindcss/forms'),
  ],
}
