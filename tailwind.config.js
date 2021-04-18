// const defaultTheme = require('tailwindcss/defaultTheme')

module.exports = {
  purge: {
    // in prod look at shadow-cljs output file in dev look at runtime, which will change files that are actually compiled; postcss watch should be a whole lot faster
    content: process.env.NODE_ENV == 'production' ? ["./shells/embed/js/main.js"] : ["./shells/embed/js/cljs-runtime/*.js"]
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
