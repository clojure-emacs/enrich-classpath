# enrich-classpath [![Clojars Project](https://img.shields.io/clojars/v/mx.cider/enrich-classpath.svg)](https://clojars.org/mx.cider/enrich-classpath)

A library (and Leiningen plugin) that automatically downloads all available `.jar`s with Java sources and javadocs for a given project, so that various tooling (typically IDEs) can access it.

It behaves gracefully even in face of `:managed-dependencies`, `:pedantic?`, in- and inter-process parallel invocations of Lein, etc.

For efficiency, it has caching that is shared across projects, and dependency resolution is parallel.

Importantly, it does not mutate the classpath via classloaders in any way, yielding a simple solution that guaranteed to work across all JDKs. 

## A sample use case

As a quick example of you can do with it: 

```clj
(defn class->source [class-object]
  {:pre [(class? class-object)]}
  (-> class-object pr-str munge (string/replace "." "/") (str ".java") (io/resource) slurp))

;; Usage: (-> Thread class->source println)
```

All what the plugin does is placing a source (and/or javadoc) `.jar` in the classpath, so that `(io/resource)` will return it (else it would return `nil`).

A great real-world lib that would be enhanced by this program is Orchard's [source-info](https://github.com/clojure-emacs/orchard/blob/f8a85feb613501be0896c3683c8ff7b0bd404061/src/orchard/java/parser.clj#L290).

## Installation prerequisites

* If using cider-nrepl, the minimum required version is 0.26.0.
* If using Orchard, the minimum required version is 0.7.1.

## Installation and usage

### Leiningen

Add the following somewhere in your `~/.lein/profiles.clj` (aka your [user-wide profile](https://github.com/technomancy/leiningen/blob/0f456829a8b21335aa86390f3ee3d0dcc68410d6/doc/PROFILES.md#declaring-profiles)):

```clj
;; Installing this plugin under the :repl profile is most recommended for best performance,
;; especially if you work with a monorepo with a complex build process.  
:repl {:middleware [cider.enrich-classpath/middleware]
       :plugins    [[mx.cider/enrich-classpath "1.4.0-alpha2"]]
       ;; Optional - you can use this option to specify a different set (e.g. a smaller set like #{"sources"} is more performant)
       :enrich-classpath {:classifiers #{"sources" "javadoc"}}}
```

> If adding this middleware on a per-project basis, make sure it's not turned on by default, simply because other people might not appreciate a slower (first) dependency resolution for a functionality that they might not use. [Profiles](https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md) help.

After that, `lein repl` and similar commands will download each artifact of your dependency tree with `"sources"` and `"javadoc"` Maven classifiers, if such an artifact exists (normally these only exist for Java dependencies, not Clojure ones), and place it in the classpath for your REPL process. 

## Notes on caching

Running this program _for the first time_ on a given project will be slow (think: anything between 1-3m). The more dependencies your project has, especially Java ones, the slower this run will be.

Each time a source or javadoc `.jar` is found, the found artifact will be logged, so that you can see that the program is in fact doing something:

```
:cider.enrich-classpath/found [org.clojure/clojure "1.10.1" :classifier "sources"]
```

After a successful run, a cache file is written to `~/.enrich-classpath-cache`. This file is shared across all projects, and will automatically grow via merge. So the first few runs in a variety of projects will result in a slow dependency resolution, and after that it will stabilize in those projects (and best-case scenario, also in _other_ projects)

Given a project with 100% cache hits (which eventually will be the case in all your projects, after a while), this program's runtime overhead will be essentially zero.

The `~/.enrich-classpath-cache` file has a stable format. You can version-control it, so that if you setup a new machine you won't have cache misses.

## Options

This program observes a number of Lein configuration options under the `:enrich-classpath` key:

#### `:classifiers`

By default, both sources and javadocs will be fetched. By specifying only `sources` to be fetched, one gets a 2x performance improvement (because 0.5x as many items will be attempted to be resolved):

```clj
:enrich-classpath {:classifiers #{"sources"}}
```

You can also specify classifiers other than `"sources", "javadoc"`, if that is useful to you.

#### `:failsafe`

By default, this program runs within a try/catch block and within a timeout. This wrapping is called the 'failsafe'
and it has the goal of preventing the plugin from possibly disrupting REPL/IDE startup.

If an error or timeout occurs, REPL startup will continue, although no entries will be added to the classpath.

Generally you want to keep this default. By specifying `:failsafe false`, you can disable this wrapping, which might ease troubleshooting.

#### `:timeout`

This is the timeout value in seconds that `:failsafe` uses. It defaults to 215.

#### `:repositories`

The Maven repositories that this program will query, in search of sources and javadocs.

Defaults to your project's Lein :repositories, typically Maven Central + Clojars + any custom repositories you may have specified.

If you specify `:repositories`, they will replace Lein's entirely. 

In all cases, repositories detected as unreachable (because of DNS, auth, etc) will be removed.

## Troubleshooting

If this program is not behaving as it should, you can debug it in isolation with the following command:

```
DEBUG=true lein with-profile +repl deps
```

The following entries can be possibly logged:

* `:cider.enrich-classpath/resolving` - a request is being performed for resolving a specific dependency (of any kind: plain, source or javadoc)
* `:cider.enrich-classpath/found` - a source/jar artifact has been found, and will be added to the classpath.
* `:cider.enrich-classpath/resolved` - a request has succeeded in resolving a specific dependency (of any kind: plain, source or javadoc) 
* `:cider.enrich-classpath/timed-out` - a given dependency request has timed out, or the program as a whole has timed out (per the `:failsafe` option).

If you wish to start from a clean slate (given that resolutions are cached, even in face of timeout), you can remove the `~/.enrich-classpath-cache` file. 

## License

This program and the accompanying materials are made available under the terms of the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0).
