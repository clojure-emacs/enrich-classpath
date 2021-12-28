.PHONY: inline-deps clean install check-env

clean:
	lein clean
	rm -f .inline-deps

.inline-deps: clean
	lein with-profile -user inline-deps
	touch .inline-deps

inline-deps: .inline-deps

install:. inline-deps
	lein with-profile -user,+plugin.mranderson/config install

# Example usage:
# copy a temporary Clojars token to your clipboard
# GIT_TAG=1.6.0 CLOJARS_USERNAME=$USER CLOJARS_PASSWORD=$(pbpaste) make deploy
# (recommended) delete said token.

# Semicolons are used so that `cd` works.
deploy: check-env inline-deps
	lein with-profile -user,-dev deploy clojars
	git tag -a "$$GIT_TAG" -m "$$GIT_TAG"
	git push
	git push --tags

check-env:
ifndef CLOJARS_USERNAME
	$(error CLOJARS_USERNAME is undefined)
endif
ifndef CLOJARS_PASSWORD
	$(error CLOJARS_PASSWORD is undefined)
endif
ifndef GIT_TAG
	$(error GIT_TAG is undefined)
endif
