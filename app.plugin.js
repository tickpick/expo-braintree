// Delegates to the compiled TypeScript plugin so there is a single
// implementation to maintain — see plugin/src/withBraintree.ts.
module.exports = require("./plugin/build/withBraintree").default;
