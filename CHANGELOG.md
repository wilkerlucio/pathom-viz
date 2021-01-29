# Change Log

## [2021.01.29]
- Support mutations in Pathom 3 trace
- Support ident in Pathom 3 trace
- Render attributes for nested traces
- Clear graph viz when trace updates
- Ability to remove queries from query history

## [2021.01.26]
- Fix trace not reloading on query editor

## [2021.01.25]
- Trace support for Pathom 3 🎉
- View plan from trace for Pathom 3 traces
- Graph View support fitting node and neighbors
- Show node details from Graph View
- Add Codemirror6 and NextJournal clojure-mode for clojure data view
- Sorted maps when rendering data in the new clojure reader

## [2021.01.22]
- Fix ident completions

## [1.2.1]
- Add new logs tab to log things from the connector
- Add graph view support on log
- Use new auto-complete algorithm based on Pathom 3 code (also works on Pathom 2)

## [1.2.0]
- Add Logs Tab
- Support graph render for Pathom 3

## [1.1.1]
- Fix show history button

## [1.1.0]
- Standalone app!
- Refactor all code to use Fulcro 3

From here up we will log things in terms of the standalone app

## [1.0.8]
- Replace ghostwheel with guardrails
- Ensure index-io is present to compute completions
- Add flag to enable/disable trace support on query editor
- Query editor supports sync and async parsers

## [1.0.7]
- Fix graph comm in attribute graph panel

## [1.0.6]
- Remove interconnections toggle from graph view
- Fix attribute network depth from groups

## [1.0.5]
- Add Index Explorer

## [1.0.4]
- Improved fuzzy search algorithm
- Change style for batch resolver on trace

## [1.0.3]
- Fixes for prod releases in tracer
- Improvements on error handlers
- Increase pathom-card default size
- Trace tooltip takes window scroll into account to position hint
- Update query editor to latest pathom, support custom trace initial size
- Add error boundary around trace component
- Auto index load is configurable on workspaces pathom card
- Support wrap-run-query on query editor to extend text

## [1.0.2]
- Fix trace: don't log trace details when click to expand/contract groups
- Add codemirror editor with pathom settings (from old OgE)
- Add workspaces card helper to create a pathom editor card
- Add workspaces card helper for pathom explorer

## [1.0.1]
- Show details of events on mouse when click with alt key

## [1.0.0]
- Initial release, includes trace view
