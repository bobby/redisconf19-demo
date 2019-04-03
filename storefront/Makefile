default: release

# Clean

.PHONY: clean-cljs
clean-cljs:
	rm -rf resources/public/js/compiled

# Dependencies

.PHONY: js-deps
js-deps:
	yarn

# Development Workflow

.PHONY: cljs-dev
cljs-dev: js-deps
	yarn shadow-cljs -A:cljs-dev watch app

.PHONY: clj-dev
clj-dev:
	clj -A:dev${CLJ_REPL_ALIAS}

# Release

.PHONY: release-cljs
release-cljs: clean-cljs js-deps
	yarn shadow-cljs -A:cljs-release release app

.PHONY: release
release: release-cljs

# Run locally, simulating deployed application

.PHONY: run
run: release-cljs
	clj -A:run