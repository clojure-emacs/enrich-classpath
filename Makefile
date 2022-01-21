.PHONY: inline-deps clean install check-env

clean:
	lein clean
	rm -f .inline-deps
	rm -rf .cpcache

.inline-deps: clean
	lein with-profile -user inline-deps
	touch .inline-deps

inline-deps: .inline-deps

install:. inline-deps
	lein with-profile -user,+plugin.mranderson/config install

deploy: check-env inline-deps
	lein with-profile -user,-dev,+plugin.mranderson/config deploy clojars

check-env:
ifndef CLOJARS_USERNAME
	$(error CLOJARS_USERNAME is undefined)
endif
ifndef CLOJARS_PASSWORD
	$(error CLOJARS_PASSWORD is undefined)
endif
