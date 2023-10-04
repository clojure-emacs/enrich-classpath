# enrich-classpath
[![Clojars Project](https://img.shields.io/clojars/v/mx.cider/enrich-classpath.svg)](https://clojars.org/mx.cider/enrich-classpath) [![Clojars Project](https://img.shields.io/clojars/v/mx.cider/tools.deps.enrich-classpath.svg)](https://clojars.org/mx.cider/tools.deps.enrich-classpath) [![Clojars Project](https://img.shields.io/clojars/v/mx.cider/lein-enrich-classpath.svg)](https://clojars.org/mx.cider/lein-enrich-classpath)

A library (and Leiningen plugin, and Clojure CLI program) that, as its main feature, automatically downloads all available `.jar`s with Java sources and javadocs for a given project, so that various tooling (typically IDEs) can access it.

This allows improved Java-facing IDE functionalities: navigation, documentation, completion and stacktraces.

It behaves gracefully even in face of `:managed-dependencies`, `:pedantic?`, in- and inter-process parallel invocations of Lein, etc.

For efficiency, it has caching that is shared across projects, and dependency resolution is parallel.

Importantly, it does not mutate the classpath via classloaders in any way, yielding a simple solution that guaranteed to work across all JDKs. 

This is the set of things that `enrich-classpath` can add to the classpath, when needed (depending on your JDK and build tool of choice):

* third-party source/javadocs .jars
* the sources for a given JDK
  * e.g. the sources for the `Thread` class.
* the `:java-source-paths` from a Leiningen project
  * this way, IDE-like functionality will also work for classes you might be developing.
* `tools.jar`.

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

## Installation and usage

> **NOTE:**
> In general, you are not expected to add any dependency or plugin to project.clj or deps.edn. Please read the following instructions carefully.  

### Emacs `cider-jack-in`

If you use Emacs CIDER, customize `cider-enrich-classpath` to `t` and simply `cider-jack-in` as normal.

It will work as usual, for Lein and tools.deps projects alike. There's a fallback to the normal commands, in case something went wrong.

### Emacs `cider-connect`

If you want to `cider-connect`, CIDER cannot automatically add enrich-classpath for you.

See the next section for a recipe.

### Any Lein or tools.deps project

You can enjoy a highly optimized setup as follows:

* Copy the [example Makefile](./examples/Makefile) to your project
  * Or merge it into your existing Makefile
* Change its `.DEFAULT_GOAL := lein-repl` to `deps-repl` if you are a tools.deps user
  * Or perhaps remove it if you want the Makefile to do something else by default.
* Edit its `LEIN_PROFILES`/`DEPS_MAIN_OPTS` to match the profiles/aliases you intend to use during development
  * You can also set `LEIN_PROFILES`/`DEPS_MAIN_OPTS` as env vars, which will take precedence.
* Run `make`
  * If using Leiningen, it will launch a nREPL server that you can connect to
    * project.clj `:repl-options` will be honored, if found: `:host :port :transport :nrepl-handler :socket :nrepl-middleware`
    * a terminal REPL will also be available.
  * If using Clojure CLI, it will honor your `-M` program (as specified in the Makefile which you should have edited)
    * Your main program can launch a repl, a nrepl server, both, or anything really.
    * Note that no nREPL server is launched, for this case, unlike we do for Leiningen.

#### Rationale

The suggested choice of Make might surprise you. However its caching (that can accurately be invalidated by modifying `project.clj`, `deps.edn` and a variety of similar files) is a very good fit for Make's offering.

The Clojure CLI popularized this style of caching (which doesn't use Makefiles internally, but is essentially equivalent).

Also note that, because we suggest that you can copy a Makefile, you can always modify it at will, and study its functioning for suggesting improvements or creating alternatives. Oftentimes tools aren't as transparent/inviting. 

If you are a Leiningen user, you will also enjoy the following advantages (vs. a traditional `lein` invocation)

* Instant startup, as enabled by the caching
  * i.e. no time is spent doing any Lein or enrich-classpath work
  * You will only experience the startup time directly related to Clojure
* Only one JVM will be spun up, instead of two
  * Leiningen typically allocates two JVMs per REPL, which isn't optimal
* More clearly understandable `java` processes
  * Typically, Leiningen would spawn `java` processes that aren't as easy to inspect as a Clojure CLI one.
    * e.g. it has a opaque "init" form stored in a tmp directory, making some stacktraces more confusing.

## Notes on caching

Running this program _for the first time_ on a given project will be slow (think: anything between 1-3m). The more dependencies your project has, especially Java ones, the slower this run will be.

Each time a source or javadoc `.jar` is found, the found artifact will be logged, so that you can see that the program is in fact doing something:

```
:cider.enrich-classpath/found [org.clojure/clojure "1.10.1" :classifier "sources"]
```

After a successful run, a cache file is written to `~/.cache/enrich-classpath-cache` (honoring `XDG_CACHE_HOME`). This file is shared across all projects, and will automatically grow via merge. So the first few runs in a variety of projects will result in a slow dependency resolution, and after that it will stabilize in those projects (and best-case scenario, also in _other_ projects)

Given a project with 100% cache hits (which eventually will be the case in all your projects, after a while), this program's runtime overhead will be essentially zero.

The `~/.cache/enrich-classpath-cache` file has a stable format. You can version-control it, so that if you setup a new machine you won't have cache misses.

## Options

This program observes a number of Lein configuration options under the `:enrich-classpath` key:

#### `:shorten`

Default: `false` for the legacy middleware, `true` for the newer offerings.

If `true`, most classpath entries will be added as a single, very thin .jar file,
which contents will consist of a single `MANIFEST.MF` file which will point of all those classpath entries.

This results in a shorter `java` process name, which avoids incurring into the length limitations that Linux programs can be subject to.

There isn't a visible difference in behavior around using this option: with or without it, entries will be added to the classpath effectively
in the same way, and e.g. `clojure.java.io/resource` will keep pointing to the right jar (i.e. the final one, not the thin one that points to it).

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

#### `:main`

_This option is specific to_ `lein-enrich-classpath` _(the newer offering for Lein)_.

String. Specifies a main program other than `nrepl.cmdline` to be be run. Includes CLI arguments.

Example: `"cognitect.test-runner --dir test"`

## Troubleshooting

If this program is not behaving as it should, you can debug it in isolation by prefixing `DEBUG=true` to its invocation:

```
# Per Makefile linked to above
DEBUG=true make lein-repl
```

The following entries can be possibly logged:

* `:cider.enrich-classpath/resolving` - a request is being performed for resolving a specific dependency (of any kind: plain, source or javadoc)
* `:cider.enrich-classpath/found` - a source/jar artifact has been found, and will be added to the classpath.
* `:cider.enrich-classpath/resolved` - a request has succeeded in resolving a specific dependency (of any kind: plain, source or javadoc) 
* `:cider.enrich-classpath/timed-out` - a given dependency request has timed out, or the program as a whole has timed out (per the `:failsafe` option).
* `:cider.enrich-classpath/failed-to-resolve` - the request for resolving a given dependency failed. 
* `:cider.enrich-classpath/omitting-empty-source` - a given source artifact (.jar) was found, but it didn't have actual Java sources in it, so it won't be added to the classpath.
* `:cider.enrich-classpath/no-jdk-sources-found` - no JDK sources could be found. Your JDK distribution (on `apt`, `rpm`, etc) probably didn't include any sources, and they should be installed separately (e.g. `sudo apt install openjdk-11-source`).

If you wish to start from a clean slate (given that resolutions are cached, even in face of timeout), you can remove the `~/.cache/enrich-classpath-cache` file. 

## License

This program and the accompanying materials are made available under the terms of the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0).
