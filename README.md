# Ittyon

[![Build Status](https://travis-ci.org/weavejester/ittyon.svg?branch=master)](https://travis-ci.org/weavejester/ittyon)

Ittyon is a simple library designed to manage state in games,
particularly distributed state in a client/server architecture. It
supports both Clojure and ClojureScript, so is particularly suitable
for web-based games.

Ittyon draws inspiration from the [entity component][1] model, but
provides a greater separation between code and data. Its data model
has a lot in common with [Datomic][2].

Ittyon's network code should be considered **experimental**.
Experimental means that the code is incomplete and subject to change.

[1]: https://en.wikipedia.org/wiki/Entity_component_system
[2]: http://www.datomic.com/

## Installation

Add the following to your project dependencies:

```clojure
[ittyon "0.11.4"]
```

## Overview

Ittyon maintains an immutable database of **facts** to describe a game
state. A fact is a vector of four elements:

```clojure
[entity aspect value time]
```

These elements are often abbreviated to `[e a v t]`.

Facts can be **asserted** or **revoked** to produce a state
**transition**. A state transition can be committed to a state to
produce an updated state.

Ittyon provides three mechanisms to customize its behavior:
**validation**, **indexing** and **reactions**.

Validation determines whether or not a transition is valid for a
particular state. If a transition is not valid, the state is not
updated.

Indexing allows the game state to be efficiently queried. Many indexes
may be defined to allow the data to be accessed in different ways.

Reactions produce transitions according to changes in state or time.
This is the mechanism Ittyon uses for turning a static world into one
that reacts to events.


## Documentation

* [Wiki](https://github.com/weavejester/ittyon/wiki)
* [API Docs](https://weavejester.github.io/ittyon)


## License

Copyright © 2016 James Reeves

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
