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

[3]: https://en.wikipedia.org/wiki/Unix_time


### Aspects

Clojure allows hierarchical relationships to be defined between
keywords via `derive`, and Ittyon takes advantage of this system to
allow aspects to share common behavior.

Ittyon comes with two base aspects you can derive from:

* `ittyon.core/aspect`
* `ittyon.core/singular`

All of your aspects should derive from `:ittyon.core/aspect`. This
adds basic validation, such as ensuring the entities are UUIDs, and
the time is a positive integer.

It also provides a way of atomically removing an entity via the
`:ittyon.core/live?` aspect. This can only be true, and must be set on
an entity before any other aspects can be set. When this aspect is
removed, the entity is removed.

By default, aspects can have multiple values per entity. The
`:ittyon.core/singular` aspect forces an aspect to have only one value
per entity. When a new value is asserted, the old value is revoked.


### Transitions

Facts can be added or removed to a state through the use of
**transitions**. Transitions contain the same elements as facts,
prefixed with an **op**.

```clojure
[op entity aspect value time]
```

The op is a keyword of either `:assert`, which adds a new fact, or
`:revoke`, which removes an existing fact.

Transitions are applied to a state with the `ittyon.core/commit`
function. When a transaction is committed, three steps are followed:

1. Validate
2. Update
3. React

Validation ensures that the transaction is valid for the current
state. This means not only validating the transaction itself, but also
ensuring that it makes sense in the context of the supplied state.

If the transition is valid, the state can be updated. This adds or
removes a fact to the state, and refreshes the current indexes.

Finally, a successfully applied transition may result in reactions.
These are additional transitions triggered by the initial one. For
example, revoking the `:ittyon.core/live?` aspect on an entity results
in all other aspects of that entity being revoked as well.


## License

Copyright © 2014 James Reeves

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
