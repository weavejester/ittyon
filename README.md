# Ittyon

[![Build Status](https://travis-ci.org/weavejester/ittyon.svg?branch=master)](https://travis-ci.org/weavejester/ittyon)

Ittyon is a simple library designed to manage state in games,
particularly distributed state in a client/server architecture. It
supports both Clojure and ClojureScript, so is particularly suitable
for web-based games.

Ittyon draws inspiration from the [entity component][1] model, but
provides a greater separation between code and data. Its data model
has a lot in common with [Datomic][2].

[1]: https://en.wikipedia.org/wiki/Entity_component_system
[2]: http://www.datomic.com/

## Installation

Add the following to your project dependencies:

```clojure
[ittyon "0.0.11"]
```

## Overview

### State

State in Ittyon is defined as a set of **facts**. A fact is a vector
of four elements:

```clojure
[entity aspect value time]
```

These are often abbreviated to `[e a v t]`.

An **entity** is a unique identifier. Ittyon uses UUIDs for this
purpose.

An **aspect** is a namespaced keyword, usually one derived from
`:ittyon.core/aspect`.

A **value** is any immutable value.

A **time** is a positive integer that ascends over time. Ittyon uses
[Unix time][3] for this purpose.

Facts can be added or removed through the use of **transitions**.
A transition adds an extra parameter to the start of a fact.

```clojure
[op entity aspect value time]
```

The op is a keyword of either `:assert`, which adds a new fact, or
`:revoke`, which removes an existing fact.

[3]: https://en.wikipedia.org/wiki/Unix_time


## License

Copyright © 2014 James Reeves

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
