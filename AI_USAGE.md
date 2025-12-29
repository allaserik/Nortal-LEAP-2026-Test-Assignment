# AI Usage

Briefly describe how you used AI (tools, prompts, code snippets, fixes). Be concise and honest (using AI is fine; it's just a tool; we want to know your method). If you did not use AI, state that here.

I used AI mainly to:
- Help interpret the assignment requirements into explicit behavioral scenarios.
- Propose a test plan (service-level and API integration tests) covering edge cases such as:
  - preventing double loans
  - reservation queue enforcement
  - immediate loan when reserving an available book
  - return authorization (only current borrower)
  - return handoff skipping missing/ineligible reservers
- Suggest a refactor for borrow-limit enforcement using a repository count query instead of scanning all books.

I validated changes by:
- Running `./gradlew test` after each change set.
- Running `./gradlew spotlessApply` and re-running tests.

Assumptions:
- Error reason strings are not part of the scoring contract (visible tests assert `ok` and state, not `reason`).
- Reservation queues should be respected even if the system enters an inconsistent state (book available with a non-empty queue), so reservations append to the queue instead of immediately loaning.
