# Change Log

## [2022.2.21]
- More resilient requests to remote clients, avoid UI locking on invalid responses

## [2022.2.15]
- Save entity data in query history
- Support lenient mode setting in query editor
- Support include stats setting in query editor

## [2022.2.14]
- Fix situation where Pathom 3 renders a Trace view with blank data
- Support report from strict errors in Pathom 3
- Resize panels now uses % instead of absolute sizes for a better app resize flow
- Render entity data in query history when available

## [2022.01.21]
- Support custom headers when adding parsers by URL
- Adding from URL now remembers the latest connections to reconnect

## [2021.07.16]
- Fix overflow size in query result panel
- Adjust indexes request to deal with Pathom 3 strict mode

## [2021.07.11]
- Support Pathom 3 Indexes directly
- Query History fix trash icon position
- Query History fix appending after overflow
- New entity editor 🎉
- Add from URL autofocus on input on dialog open
- Allow display of empty query history
- Display error indicators on query editor and entity editor

## [2021.05.13]
- Double-click on graph view to fit content
- Mac App Signed
- Auto updates

## [2021.05.11]
- Fix trace bug when all children disappear when the root children has over 20 items
- Ensure consistent background on CM6
- Show node details on snapshots
- Improve performance of snapshots rendering by lazy processing the elements
- Fix bug when sorting maps with irregular values
- Fix bug on trace that made tooltip stay on screen
- Remove node zoom on click on the graph view
- Use Tailwind JIT in the app
- Fix trace exceptions when trace is blank
- Error boundaries around each connection and log entries

## [2021.04.22]
- In the request tab, recent requests now show on top of the list
- Request tab items now have a border at the right to make easier to scroll the list
- Request tab max size increased to cover 5 lines of query

## [2021.04.21]
- Breaking change: messaging to log was modified
- View node details on graph log
- New feature to see full graph data (from any node details section)

## [2021.02.24]
- Many UI Tweeks
- Fix to auto-complete: attributes that depends on multiple inputs were missing
- Add bigger minimum size to avoid elements fully collapsing away

## [2021.02.01]
- Bump Pathom 3 to fix auto-complete case that freezes the app

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
