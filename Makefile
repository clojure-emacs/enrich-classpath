.PHONY: inline-deps clean install-base install-deps install check-env check-install-env

clean:
	lein clean
	rm -f .inline-deps
	rm -rf .cpcache

.inline-deps: clean
	lein with-profile -user inline-deps
	touch .inline-deps

inline-deps: .inline-deps

install-base: .inline-deps check-install-env
	lein with-profile -user,+plugin.mranderson/config install

install-deps: install-base
	cd tools.deps; lein with-profile -user install

install-plugin: install-base
	cd lein-plugin; lein with-profile -user install

# Usage: PROJECT_VERSION=1.15.4 make install
# PROJECT_VERSION is needed because it's not computed dynamically
install: install-base install-deps install-plugin

deploy: check-env inline-deps
	lein with-profile -user,-dev,+plugin.mranderson/config deploy clojars
	cd tools.deps; lein with-profile -user deploy clojars
	cd lein-plugin; lein with-profile -user deploy clojars

check-env:
ifndef CLOJARS_USERNAME
	$(error CLOJARS_USERNAME is undefined)
endif
ifndef CLOJARS_PASSWORD
	$(error CLOJARS_PASSWORD is undefined)
endif

check-install-env:
ifndef PROJECT_VERSION
	$(error Please set PROJECT_VERSION as an env var beforehand.)
endif
