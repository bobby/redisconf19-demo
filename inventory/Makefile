default: release

# Clean

.PHONY: clean-cljs
clean-cljs:
	rm -rf resources/public/js/compiled

.PHONY: clean
clean: clean-cljs

# Dependencies

.PHONY: bootstrap
bootstrap:
	brew install clojure yarn

.PHONY: js-deps
js-deps:
	yarn

# Development Workflow

.PHONY: cljs-dev
cljs-dev: js-deps clean-cljs
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

# Project info

.PHONY: loc
loc:
	tokei src/
