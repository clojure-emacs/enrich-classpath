.PHONY: lein-repl deps-repl
HOME=$(shell echo $$HOME)
HERE=$(shell echo $$PWD)

# This makefile offers high-performance (cached) wrappers that enrich the classpath, for optimal IDE functioning.
# Feel free to suggest any improvements at:
# https://github.com/clojure-emacs/enrich-classpath/issues

# Allows you to simply type `make`, the default task will be run for you.
# Feel free to choose one of: lein-repl | deps-repl
.DEFAULT_GOAL := lein-repl

# Set bash instead of sh for the @if [[ conditions,
# and use the usual safety flags:
SHELL = /bin/bash -Eeu

# The Lein profiles that will be selected for `lein-repl`.
# Feel free to upgrade this, or to override it with an env var named LEIN_PROFILES.
# Expected format: "+dev,+test"
# Don't use spaces here.
LEIN_PROFILES ?= "+dev,+test"

# The Clojure CLI aliases that will be selected for main options for `deps-repl`.
# Feel free to upgrade this, or to override it with an env var named DEPS_MAIN_OPTS.
# Expected format: "-M:alias1:alias2"
DEPS_MAIN_OPTS ?= "-M:dev:test"

# The enrich-classpath version to be injected.
# Feel free to upgrade this.
ENRICH_CLASSPATH_VERSION="1.19.0"

# Create and cache a `java` command. project.clj is mandatory; the others are optional but are taken into account for cache recomputation.
# It's important not to silence with step with @ syntax, so that Enrich progress can be seen as it resolves dependencies.
.enrich-classpath-lein-repl: Makefile project.clj $(wildcard checkouts/*/project.clj) $(wildcard deps.edn) $(wildcard $(HOME)/.clojure/deps.edn) $(wildcard profiles.clj) $(wildcard $(HOME)/.lein/profiles.clj) $(wildcard $(HOME)/.lein/profiles.d) $(wildcard /etc/leiningen/profiles.clj)
	bash 'lein' 'update-in' ':plugins' 'conj' "[mx.cider/lein-enrich-classpath \"$(ENRICH_CLASSPATH_VERSION)\"]" '--' 'with-profile' $(LEIN_PROFILES) 'update-in' ':middleware' 'conj' 'cider.enrich-classpath.plugin-v2/middleware' '--' 'repl' | grep " -cp " > $@

# Launches a repl, falling back to vanilla lein repl if something went wrong during classpath calculation.
lein-repl: .enrich-classpath-lein-repl
	@if grep --silent " -cp " .enrich-classpath-lein-repl; then \
		export YOURKIT_SESSION_NAME="$(basename $(PWD))"; \
		eval "$$(cat .enrich-classpath-lein-repl) --interactive"; \
	else \
		export YOURKIT_SESSION_NAME="$(basename $(PWD))"; \
		echo "Falling back to lein repl... (you can avoid further falling back by removing .enrich-classpath-lein-repl)"; \
		lein with-profiles $(LEIN_PROFILES) repl; \
	fi

# Create and cache a `clojure` command. deps.edn is mandatory; the others are optional but are taken into account for cache recomputation.
# It's important not to silence with step with @ syntax, so that Enrich progress can be seen as it resolves dependencies.
.enrich-classpath-deps-repl: Makefile deps.edn $(wildcard $(HOME)/.clojure/deps.edn) $(wildcard $(XDG_CONFIG_HOME)/.clojure/deps.edn)
	cd $$(mktemp -d -t enrich-classpath.XXXXXX); clojure -Sforce -Srepro -J-XX:-OmitStackTraceInFastThrow -J-Dclojure.main.report=stderr -Sdeps '{:deps {mx.cider/tools.deps.enrich-classpath {:mvn/version $(ENRICH_CLASSPATH_VERSION)}}}' -M -m cider.enrich-classpath.clojure "clojure" "$(HERE)" "true" $(DEPS_MAIN_OPTS) | grep "^clojure" > $(HERE)/$@

# Launches a repl, falling back to vanilla Clojure repl if something went wrong during classpath calculation.
deps-repl: .enrich-classpath-deps-repl
	@if grep --silent "^clojure" .enrich-classpath-deps-repl; then \
		eval $$(cat .enrich-classpath-deps-repl); \
	else \
		echo "Falling back to Clojure repl... (you can avoid further falling back by removing .enrich-classpath-deps-repl)"; \
		clojure $(DEPS_MAIN_OPTS); \
	fi
